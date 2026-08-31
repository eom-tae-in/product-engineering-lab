package kr.savepick.order.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import kr.savepick.order.domain.OrderStatusHistory;

/** API-024·API-114 공용. */
public record OrderStatusHistoryResponse(String fromStatus, String toStatus, String actorType, OffsetDateTime occurredAt) {

    public static OrderStatusHistoryResponse from(OrderStatusHistory history, ZoneId zone) {
        return new OrderStatusHistoryResponse(
                history.getFromStatus() == null ? null : history.getFromStatus().name(),
                history.getToStatus().name(), history.getActorType().name(),
                history.getOccurredAt().atZone(zone).toOffsetDateTime());
    }
}
