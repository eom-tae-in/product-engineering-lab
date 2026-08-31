package kr.savepick.order.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import kr.savepick.order.application.OrderAbandonService.AbandonResult;

/** API-019. */
public record AbandonOrderResponse(Long orderId, String status, OffsetDateTime releasedAt) {

    public static AbandonOrderResponse from(AbandonResult result, ZoneId zone) {
        return new AbandonOrderResponse(result.orderId(), result.status().name(), result.releasedAt().atZone(zone).toOffsetDateTime());
    }
}
