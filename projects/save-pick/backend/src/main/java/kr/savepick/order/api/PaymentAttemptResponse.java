package kr.savepick.order.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import kr.savepick.order.payment.PaymentAttempt;

/** API-114. */
public record PaymentAttemptResponse(
        int attemptNo, String status, String failureReason, OffsetDateTime requestedAt, OffsetDateTime resolvedAt) {

    public static PaymentAttemptResponse from(PaymentAttempt attempt, ZoneId zone) {
        return new PaymentAttemptResponse(
                attempt.getAttemptNo(), attempt.getStatus().name(),
                attempt.getFailureReason() == null ? null : attempt.getFailureReason().name(),
                attempt.getRequestedAt().atZone(zone).toOffsetDateTime(),
                attempt.getResolvedAt() == null ? null : attempt.getResolvedAt().atZone(zone).toOffsetDateTime());
    }
}
