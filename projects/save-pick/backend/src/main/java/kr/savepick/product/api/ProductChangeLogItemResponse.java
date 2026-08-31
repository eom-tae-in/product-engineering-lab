package kr.savepick.product.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import kr.savepick.product.application.ProductQueryService.ChangeLogItem;

/** API-107. */
public record ProductChangeLogItemResponse(
        String changedField, String beforeValue, String afterValue, String actorName, OffsetDateTime changedAt) {

    public static ProductChangeLogItemResponse from(ChangeLogItem item, ZoneId zone) {
        return new ProductChangeLogItemResponse(
                item.changedField(), item.beforeValue(), item.afterValue(), item.actorName(),
                item.changedAt().atZone(zone).toOffsetDateTime());
    }
}
