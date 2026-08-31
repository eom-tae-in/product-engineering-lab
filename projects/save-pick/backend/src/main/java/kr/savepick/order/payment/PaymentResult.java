package kr.savepick.order.payment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import kr.savepick.order.domain.OrderStatus;

/**
 * API-022 결제 처리 결과. 성공·실패 두 갈래를 하나의 타입으로 표현한다(11-api-spec.md §5).
 * 조건부 UPDATE로 주문 상태를 바꾼 직후에는 이미 읽어 둔 {@code Order} 엔티티의 해당 필드가
 * 낡은 값일 수 있어(14-project-structure.md §9.2 — 더티 체킹이 아닌 명시적 쿼리), 엔티티를
 * 그대로 담지 않고 응답에 필요한 값만 이 시점에 계산한 그대로 옮겨 담는다.
 */
public record PaymentResult(
        boolean succeeded,
        Long orderId,
        String orderNo,
        OrderStatus status,
        int attemptNo,
        int paymentAttemptRemaining,
        PaymentFailureReason failureReason,
        boolean holdReleased,
        Integer paidAmount,
        Short pickupNumber,
        LocalDate pickupBusinessDate,
        LocalDateTime pickupStartAt,
        LocalDateTime pickupEndAt,
        LocalDateTime cancelableUntil,
        LocalDateTime noShowDueAt,
        LocalDateTime confirmedAt,
        LocalDateTime holdExpiresAt,
        Long holdRemainingSeconds,
        LocalDateTime serverTime) {

    public static PaymentResult succeeded(
            Long orderId, String orderNo, int paidAmount, short pickupNumber, LocalDate pickupBusinessDate,
            LocalDateTime pickupStartAt, LocalDateTime pickupEndAt, LocalDateTime cancelableUntil,
            LocalDateTime noShowDueAt, LocalDateTime confirmedAt, LocalDateTime serverTime) {
        return new PaymentResult(
                true, orderId, orderNo, OrderStatus.CONFIRMED, 0, 0, null, false, paidAmount, pickupNumber,
                pickupBusinessDate, pickupStartAt, pickupEndAt, cancelableUntil, noShowDueAt, confirmedAt,
                null, null, serverTime);
    }

    public static PaymentResult failed(
            Long orderId, String orderNo, OrderStatus status, int attemptNo, int paymentAttemptRemaining,
            PaymentFailureReason failureReason, boolean holdReleased, LocalDateTime holdExpiresAt,
            Long holdRemainingSeconds, LocalDateTime serverTime) {
        return new PaymentResult(
                false, orderId, orderNo, status, attemptNo, paymentAttemptRemaining, failureReason, holdReleased,
                null, null, null, null, null, null, null, null, holdExpiresAt, holdRemainingSeconds, serverTime);
    }

    /** 결과 재구성(동일 Idempotency-Key 재전송, 13번 §4) 시 이미 CONFIRMED로 끝난 성공 기록을 옮긴다. */
    public static PaymentResult fromExistingSucceeded(
            Long orderId, String orderNo, int paidAmount, short pickupNumber, LocalDate pickupBusinessDate,
            LocalDateTime pickupStartAt, LocalDateTime pickupEndAt, LocalDateTime cancelableUntil,
            LocalDateTime noShowDueAt, LocalDateTime confirmedAt, LocalDateTime serverTime) {
        return succeeded(
                orderId, orderNo, paidAmount, pickupNumber, pickupBusinessDate, pickupStartAt, pickupEndAt,
                cancelableUntil, noShowDueAt, confirmedAt, serverTime);
    }
}
