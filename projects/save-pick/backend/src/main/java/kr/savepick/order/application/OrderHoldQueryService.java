package kr.savepick.order.application;

import java.time.Duration;
import java.time.LocalDateTime;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.order.domain.Order;
import kr.savepick.order.domain.OrderRepository;
import kr.savepick.order.domain.OrderStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-018 선점 잔여 시간 조회 (BR-007·008·028). 조회 시점에 자기 주문에 한해 만료를 지연
 * 정리한다(13번 §2 (e)) — BATCH-01을 기다리지 않고 조회한 고객에게는 즉시 정확한 상태를
 * 돌려준다. 실제 종결은 {@link OrderExpiryService#expirePendingOrderIfDue}가 맡는다 — BATCH-01·
 * API-022와 같은 경로를 공유한다(2단계에서 1단계의 RELEASED/HOLD_RELEASE 절충을 EXPIRED/
 * HOLD_EXPIRE로 바로잡았다, 과제 지시).
 */
@Service
public class OrderHoldQueryService {

    private final OrderRepository orderRepository;
    private final OrderExpiryService orderExpiryService;
    private final ServerClock serverClock;
    private final Duration expiringSoonThreshold;
    private final int paymentMaxAttempts;

    public OrderHoldQueryService(
            OrderRepository orderRepository,
            OrderExpiryService orderExpiryService,
            ServerClock serverClock,
            @Value("${savepick.hold.expiring-soon-threshold}") String expiringSoonThreshold,
            @Value("${savepick.payment.max-attempts}") int paymentMaxAttempts) {
        this.orderRepository = orderRepository;
        this.orderExpiryService = orderExpiryService;
        this.serverClock = serverClock;
        this.expiringSoonThreshold = Duration.parse(expiringSoonThreshold);
        this.paymentMaxAttempts = paymentMaxAttempts;
    }

    @Transactional
    public HoldStatusResult getHoldStatus(Long orderId, Long memberId) {
        LocalDateTime now = serverClock.now();
        orderExpiryService.expirePendingOrderIfDue(orderId, memberId, now);
        Order order = orderRepository.findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        long remainingSeconds = order.getStatus() == OrderStatus.PENDING
                ? Math.max(0, Duration.between(now, order.getHoldExpiresAt()).toSeconds())
                : 0;
        boolean expiringSoon = order.getStatus() == OrderStatus.PENDING && remainingSeconds <= expiringSoonThreshold.toSeconds();
        int paymentAttemptRemaining = Math.max(0, paymentMaxAttempts - order.getPaymentAttemptCount());

        return new HoldStatusResult(order, now, remainingSeconds, expiringSoon, paymentAttemptRemaining);
    }

    public record HoldStatusResult(
            Order order, LocalDateTime serverTime, long holdRemainingSeconds, boolean expiringSoon, int paymentAttemptRemaining) {
    }
}
