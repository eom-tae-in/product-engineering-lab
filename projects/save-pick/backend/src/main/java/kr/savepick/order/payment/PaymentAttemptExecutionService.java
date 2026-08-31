package kr.savepick.order.payment;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import kr.savepick.common.audit.ActorType;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.order.domain.CancelDeadlinePolicy;
import kr.savepick.order.domain.Order;
import kr.savepick.order.domain.OrderRepository;
import kr.savepick.order.domain.OrderStatus;
import kr.savepick.order.domain.OrderStatusHistory;
import kr.savepick.order.domain.OrderStatusHistoryRepository;
import kr.savepick.pickup.application.PickupNumberIssuer;
import kr.savepick.pickup.application.PickupSlotReserveService;
import kr.savepick.pickup.domain.PickupSlot;
import kr.savepick.pickup.domain.PickupSlotRepository;
import kr.savepick.stock.application.InventoryHoldService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-022 가상 결제 요청의 실제 트랜잭션 본문(13번 §4 절차 1,3~7). {@code order/payment
 * .PaymentAttemptService}(오케스트레이터, 비트랜잭션)가 만료 사전 검사(2단계, {@code
 * OrderExpiryService})를 먼저 끝낸 뒤에만 이 서비스를 호출한다. 같은 빈 안에서 자기 자신을
 * 호출하면(self-invocation) {@code @Transactional} 프록시가 걸리지 않으므로 별도 빈으로 둔다.
 *
 * <p>이 메서드 안에서 SLOT_FULL·PICKUP_NUMBER_EXHAUSTED가 발생하면 트랜잭션 전체가 롤백된다 —
 * 그래야 이미 반영한 {@code InventoryHoldService.confirmHolds}(HELD → CONSUMED)가 되돌아가
 * "선점은 유지됨"(13번 §4 실패 표)이 실제로 성립한다. 그 대가로 이 경우 {@code payment_attempts}
 * 시도 기록도 함께 롤백된다 — 정원·번호 소진은 고객의 결제 시도 실패가 아니라 그 순간의 자원
 * 경합이므로, 시도 횟수를 소모시키지 않는 편이 BR-012의 취지(카드 오입력 같은 "고객이 통제하는"
 * 실패만 횟수를 깎는다)에 맞다고 판단했다(가정, 2단계 구현 보고 참고).
 */
@Service
public class PaymentAttemptExecutionService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PickupSlotRepository pickupSlotRepository;
    private final PickupSlotReserveService pickupSlotReserveService;
    private final PickupNumberIssuer pickupNumberIssuer;
    private final InventoryHoldService inventoryHoldService;
    private final VirtualPaymentGateway virtualPaymentGateway;
    private final int paymentMaxAttempts;
    private final Duration reservationCloseBefore;
    private final Duration customerCancelDeadline;
    private final Duration noShowGrace;

    public PaymentAttemptExecutionService(
            OrderRepository orderRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            PickupSlotRepository pickupSlotRepository,
            PickupSlotReserveService pickupSlotReserveService,
            PickupNumberIssuer pickupNumberIssuer,
            InventoryHoldService inventoryHoldService,
            VirtualPaymentGateway virtualPaymentGateway,
            @Value("${savepick.payment.max-attempts}") int paymentMaxAttempts,
            @Value("${savepick.pickup.reservation-close-before}") String reservationCloseBefore,
            @Value("${savepick.order.customer-cancel-deadline}") String customerCancelDeadline,
            @Value("${savepick.pickup.no-show-grace}") String noShowGrace) {
        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.pickupSlotRepository = pickupSlotRepository;
        this.pickupSlotReserveService = pickupSlotReserveService;
        this.pickupNumberIssuer = pickupNumberIssuer;
        this.inventoryHoldService = inventoryHoldService;
        this.virtualPaymentGateway = virtualPaymentGateway;
        this.paymentMaxAttempts = paymentMaxAttempts;
        this.reservationCloseBefore = Duration.parse(reservationCloseBefore);
        this.customerCancelDeadline = Duration.parse(customerCancelDeadline);
        this.noShowGrace = Duration.parse(noShowGrace);
    }

    @Transactional
    public PaymentResult execute(Long orderId, Long memberId, int amount, String idempotencyKey, LocalDateTime now) {
        // 1) orders FOR UPDATE, 본인 확인, 상태 확인
        Order order = orderRepository.findByIdAndMemberIdForUpdate(orderId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_STATUS, ErrorCode.INVALID_ORDER_STATUS.defaultMessage(),
                    Map.of("currentStatus", order.getStatus().name()));
        }
        // 이 시점에도 만료됐다면(사전 검사와 이 트랜잭션 시작 사이의 극히 좁은 경합 — 별도
        // 트랜잭션으로 커밋해야 하는 종결 처리를 여기서 다시 시도하면 자기 자신이 쥔 락과
        // 부딪힌다) 여기서는 durable하게 종결하지 않고 즉시 반환한다. 최대 30초 뒤 BATCH-01이나
        // 고객의 다음 조회(API-018)가 durable하게 종결한다 — 표시·판정은 이미 정확하다(BR-008).
        if (order.isHoldExpired(now)) {
            throw new BusinessException(ErrorCode.HOLD_EXPIRED);
        }

        // 3) 금액 일치 (BR-029) — 시도 기록을 만들지 않는다.
        if (amount != order.getTotalAmount()) {
            throw new BusinessException(
                    ErrorCode.AMOUNT_MISMATCH, ErrorCode.AMOUNT_MISMATCH.defaultMessage(),
                    Map.of("expectedAmount", order.getTotalAmount()));
        }

        // 4) 시간대 지정·예약 마감 재확인 (BR-015, BR-016)
        if (order.getPickupSlotId() == null) {
            throw new BusinessException(ErrorCode.SLOT_NOT_SELECTED);
        }
        PickupSlot slot = pickupSlotRepository.findById(order.getPickupSlotId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));
        if (now.isAfter(slot.getStartAt().minus(reservationCloseBefore))) {
            throw new BusinessException(ErrorCode.SLOT_CLOSED);
        }

        // 5) 결제 시도 기록
        int nextAttemptNo = order.getPaymentAttemptCount() + 1;
        if (PaymentRetryPolicy.exceedsLimit(nextAttemptNo, paymentMaxAttempts)) {
            throw new BusinessException(ErrorCode.PAYMENT_ATTEMPT_EXCEEDED);
        }
        PaymentAttempt attempt = PaymentAttempt.request(orderId, (short) nextAttemptNo, amount, idempotencyKey, now);
        attempt = paymentAttemptRepository.save(attempt);
        orderRepository.incrementPaymentAttemptCount(orderId, now);

        // 6) 가상 결제 판정 (즉시 반환, IC-A4)
        VirtualPaymentGateway.PaymentJudgement judgement = virtualPaymentGateway.judge(orderId, amount);

        if (judgement.succeeded()) {
            return succeed(order, orderId, memberId, attempt, slot, now);
        }
        return fail(order, orderId, memberId, attempt, nextAttemptNo, judgement.failureReason(), now);
    }

    private PaymentResult succeed(Order order, Long orderId, Long memberId, PaymentAttempt attempt, PickupSlot slot, LocalDateTime now) {
        // product_stocks FOR UPDATE(product_id 오름차순) → HELD → CONSUMED
        inventoryHoldService.confirmHolds(orderId, ActorType.CUSTOMER, memberId);

        // pickup_slots FOR UPDATE, 정원 확인 후 +1 (아니면 SLOT_FULL — 전체 롤백, 선점은 유지)
        pickupSlotReserveService.occupy(slot.getId());

        // pickup_number_seqs — 확정 시에만 발급(BR-026). 소진되면 전체 롤백.
        short pickupNumber = pickupNumberIssuer.issue(now.toLocalDate());

        LocalDateTime cancelableUntil = CancelDeadlinePolicy.cancelableUntil(slot.getStartAt(), customerCancelDeadline);
        LocalDateTime noShowDueAt = slot.getEndAt().plus(noShowGrace);

        orderRepository.confirmPending(orderId, pickupNumber, cancelableUntil, noShowDueAt, now, now);
        orderStatusHistoryRepository.save(OrderStatusHistory.record(
                orderId, OrderStatus.PENDING, OrderStatus.CONFIRMED, ActorType.CUSTOMER, memberId, null, now));

        attempt.resolveSucceeded(now);
        paymentAttemptRepository.save(attempt);

        return PaymentResult.succeeded(
                orderId, order.getOrderNo(), attempt.getRequestedAmount(), pickupNumber, slot.getSlotDate(),
                slot.getStartAt(), slot.getEndAt(), cancelableUntil, noShowDueAt, now, now);
    }

    private PaymentResult fail(
            Order order, Long orderId, Long memberId, PaymentAttempt attempt, int attemptNo,
            PaymentFailureReason failureReason, LocalDateTime now) {
        attempt.resolveFailed(failureReason, now);
        paymentAttemptRepository.save(attempt);

        boolean isFinal = PaymentRetryPolicy.isFinalAttempt(attemptNo, paymentMaxAttempts);
        if (!isFinal) {
            long holdRemainingSeconds = Math.max(0, Duration.between(now, order.getHoldExpiresAt()).toSeconds());
            return PaymentResult.failed(
                    orderId, order.getOrderNo(), OrderStatus.PENDING, attemptNo,
                    Math.max(0, paymentMaxAttempts - attemptNo), failureReason, false,
                    order.getHoldExpiresAt(), holdRemainingSeconds, now);
        }

        // 3회째 실패 — 즉시 해제(BR-012-3)
        inventoryHoldService.releaseHolds(orderId, ActorType.CUSTOMER, memberId);
        orderRepository.failPending(orderId, now);
        orderStatusHistoryRepository.save(OrderStatusHistory.record(
                orderId, OrderStatus.PENDING, OrderStatus.FAILED, ActorType.CUSTOMER, memberId, null, now));

        return PaymentResult.failed(
                orderId, order.getOrderNo(), OrderStatus.FAILED, attemptNo, 0, failureReason, true, null, null, now);
    }
}
