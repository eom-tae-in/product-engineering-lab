package kr.savepick.product.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import kr.savepick.product.application.ProductStatusService.StatusChangeResult;

/** API-106. */
public record ChangeProductStatusResponse(
        Long productId, String status, OffsetDateTime changedAt, int keptHoldCount, int keptConfirmedOrderCount) {

    public static ChangeProductStatusResponse from(StatusChangeResult result, ZoneId zone) {
        return new ChangeProductStatusResponse(
                result.product().getId(), result.product().getStatus().name(),
                result.changedAt().atZone(zone).toOffsetDateTime(), result.keptHoldCount(), result.keptConfirmedOrderCount());
    }
}
