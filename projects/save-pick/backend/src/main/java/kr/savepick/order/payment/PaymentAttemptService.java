package kr.savepick.order.payment;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.order.application.OrderExpiryService;
import kr.savepick.order.domain.Order;
import kr.savepick.order.domain.OrderRepository;
import kr.savepick.order.domain.OrderStatus;
import kr.savepick.pickup.domain.PickupSlot;
import kr.savepick.pickup.domain.PickupSlotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * API-022 가상 결제 요청 (11-api-spec.md §5, 13번 §4). 트랜잭션 경계를 직접 갖지 않는 얇은
 * 오케스트레이터다 — 만료 사전 검사({@link OrderExpiryService}, 독립된 트랜잭션으로 즉시 커밋)와
 * 본 결제 처리({@link PaymentAttemptExecutionService}, 별도 트랜잭션)를 순서대로 호출한다.
 *
 * <p>왜 이 클래스 자체가 {@code @Transactional}이 아닌가: 선점 만료 종결(2단계)은 실패 응답을
 * 돌려주기 전에 반드시 커밋돼야 한다(13번 §4 "COMMIT 후 409 HOLD_EXPIRED"). 만약 이 메서드
 * 전체를 하나의 트랜잭션으로 감싸고 그 안에서 만료 종결 후 예외를 던지면, 스프링 트랜잭션의
 * 기본 동작(런타임 예외 시 롤백)이 방금 커밋하려던 만료 처리까지 되돌려버린다. 그렇다고
 * {@code REQUIRES_NEW}로 중첩 트랜잭션을 쓰면, 본 결제 처리가 같은 주문 행을 다시 {@code FOR
 * UPDATE}로 잠그려 할 때 아직 끝나지 않은 바깥 트랜잭션의 락과 자기 자신이 경합하는 교착이
 * 생긴다. 이 메서드를 트랜잭션 경계 밖에 두고 두 협력자를 순차 호출하면(각자 자기 트랜잭션을
 * 열고 완전히 커밋한 뒤 돌아온다) 두 문제 모두 생기지 않는다.
 */
@Service
public class PaymentAttemptService {

    private final OrderRepository orderRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PickupSlotRepository pickupSlotRepository;
    private final OrderExpiryService orderExpiryService;
    private final PaymentAttemptExecutionService paymentAttemptExecutionService;
    private final ServerClock serverClock;
    private final int paymentMaxAttempts;

    public PaymentAttemptService(
            OrderRepository orderRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            PickupSlotRepository pickupSlotRepository,
            OrderExpiryService orderExpiryService,
            PaymentAttemptExecutionService paymentAttemptExecutionService,
            ServerClock serverClock,
            @Value("${savepick.payment.max-attempts}") int paymentMaxAttempts) {
        this.orderRepository = orderRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.pickupSlotRepository = pickupSlotRepository;
        this.orderExpiryService = orderExpiryService;
        this.paymentAttemptExecutionService = paymentAttemptExecutionService;
        this.serverClock = serverClock;
        this.paymentMaxAttempts = paymentMaxAttempts;
    }

    public PaymentResult pay(Long orderId, Long memberId, int amount, String idempotencyKey) {
        LocalDateTime now = serverClock.now();

        // 같은 Idempotency-Key 재전송 — 기존 시도 결과를 그대로 반환하고 절차를 다시 밟지 않는다
        // (13번 §4 "중복 요청 처리"). 시도 횟수도 늘지 않는다.
        Optional<PaymentAttempt> existing = paymentAttemptRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return resultFromExisting(existing.get(), memberId, now);
        }

        // 2) 선점 만료 사전 검사 — 만료됐으면 종결을 커밋하고 시도 기록 없이 409.
        boolean expiredNow = orderExpiryService.expirePendingOrderIfDue(orderId, memberId, now);
        if (expiredNow) {
            throw new BusinessException(ErrorCode.HOLD_EXPIRED);
        }

        return paymentAttemptExecutionService.execute(orderId, memberId, amount, idempotencyKey, now);
    }

    private PaymentResult resultFromExisting(PaymentAttempt existing, Long memberId, LocalDateTime now) {
        Order order = orderRepository.findById(existing.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        if (existing.getStatus() == PaymentAttemptStatus.SUCCEEDED) {
            PickupSlot slot = pickupSlotRepository.findById(order.getPickupSlotId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));
            return PaymentResult.fromExistingSucceeded(
                    order.getId(), order.getOrderNo(), existing.getRequestedAmount(), order.getPickupNumber(),
                    order.getPickupBusinessDate(), slot.getStartAt(), slot.getEndAt(), order.getCancelableUntil(),
                    order.getNoShowDueAt(), order.getConfirmedAt(), now);
        }

        int attemptNo = existing.getAttemptNo();
        boolean isFinal = PaymentRetryPolicy.isFinalAttempt(attemptNo, paymentMaxAttempts);
        if (isFinal || order.getStatus() != OrderStatus.PENDING) {
            return PaymentResult.failed(
                    order.getId(), order.getOrderNo(), order.getStatus() == OrderStatus.PENDING ? OrderStatus.FAILED : order.getStatus(),
                    attemptNo, 0, existing.getFailureReason(), true, null, null, now);
        }
        long holdRemainingSeconds = Math.max(0, Duration.between(now, order.getHoldExpiresAt()).toSeconds());
        return PaymentResult.failed(
                order.getId(), order.getOrderNo(), order.getStatus(), attemptNo, Math.max(0, paymentMaxAttempts - attemptNo),
                existing.getFailureReason(), false, order.getHoldExpiresAt(), holdRemainingSeconds, now);
    }
}
