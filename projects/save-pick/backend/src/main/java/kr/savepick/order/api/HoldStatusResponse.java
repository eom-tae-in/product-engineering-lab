package kr.savepick.order.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import kr.savepick.order.application.OrderHoldQueryService.HoldStatusResult;

/** API-018. */
public record HoldStatusResponse(
        Long orderId, String status, OffsetDateTime serverTime, OffsetDateTime holdExpiresAt,
        long holdRemainingSeconds, boolean expiringSoon, int paymentAttemptRemaining) {

    public static HoldStatusResponse from(HoldStatusResult result, ZoneId zone) {
        return new HoldStatusResponse(
                result.order().getId(),
                result.order().getStatus().name(),
                result.serverTime().atZone(zone).toOffsetDateTime(),
                result.order().getHoldExpiresAt() == null ? null : result.order().getHoldExpiresAt().atZone(zone).toOffsetDateTime(),
                result.holdRemainingSeconds(),
                result.expiringSoon(),
                result.paymentAttemptRemaining());
    }
}
