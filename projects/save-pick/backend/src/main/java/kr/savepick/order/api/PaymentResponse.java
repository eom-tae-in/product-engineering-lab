package kr.savepick.order.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import kr.savepick.order.payment.PaymentResult;

/** API-022 (11-api-spec.md §5) — 성공·실패 두 갈래를 한 타입으로 표현한다. */
public record PaymentResponse(
        String result,
        String code,
        Long orderId,
        String orderNo,
        String status,
        Integer attemptNo,
        Integer paymentAttemptRemaining,
        OffsetDateTime holdExpiresAt,
        Long holdRemainingSeconds,
        String failureReason,
        Boolean holdReleased,
        String pickupNumber,
        LocalDate pickupBusinessDate,
        OffsetDateTime pickupStartAt,
        OffsetDateTime pickupEndAt,
        Integer paidAmount,
        OffsetDateTime cancelableUntil,
        OffsetDateTime noShowDueAt,
        OffsetDateTime confirmedAt,
        String message) {

    public static PaymentResponse from(PaymentResult result, ZoneId zone) {
        if (result.succeeded()) {
            return new PaymentResponse(
                    "SUCCEEDED", null, result.orderId(), result.orderNo(), result.status().name(),
                    null, null, null, null, null, null,
                    String.format("%03d", result.pickupNumber()), result.pickupBusinessDate(),
                    offset(result.pickupStartAt(), zone), offset(result.pickupEndAt(), zone), result.paidAmount(),
                    offset(result.cancelableUntil(), zone), offset(result.noShowDueAt(), zone), offset(result.confirmedAt(), zone),
                    null);
        }
        boolean isFinal = result.status().name().equals("FAILED");
        String message = isFinal
                ? "결제가 3회 실패해 주문이 종료됐습니다. 다시 주문해 주세요."
                : "결제가 실패했습니다. 남은 시간 안에 다시 시도할 수 있습니다.";
        return new PaymentResponse(
                "FAILED", "PAYMENT_FAILED", result.orderId(), result.orderNo(), result.status().name(),
                result.attemptNo(), result.paymentAttemptRemaining(), offset(result.holdExpiresAt(), zone),
                result.holdRemainingSeconds(), result.failureReason() == null ? null : result.failureReason().name(),
                result.holdReleased(), null, null, null, null, null, null, null, null, message);
    }

    private static OffsetDateTime offset(java.time.LocalDateTime value, ZoneId zone) {
        return value == null ? null : value.atZone(zone).toOffsetDateTime();
    }
}
