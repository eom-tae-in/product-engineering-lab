package kr.savepick.order.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import kr.savepick.order.application.OrderQueryService.CustomerOrderListItem;

/** API-023. */
public record OrderListItemResponse(
        Long orderId, String orderNo, OffsetDateTime orderedAt, String status,
        OffsetDateTime pickupStartAt, OffsetDateTime pickupEndAt, String pickupNumber, int totalAmount, String itemSummary) {

    public static OrderListItemResponse from(CustomerOrderListItem item, ZoneId zone) {
        return new OrderListItemResponse(
                item.orderId(), item.orderNo(), item.orderedAt().atZone(zone).toOffsetDateTime(), item.status().name(),
                item.pickupStartAt() == null ? null : item.pickupStartAt().atZone(zone).toOffsetDateTime(),
                item.pickupEndAt() == null ? null : item.pickupEndAt().atZone(zone).toOffsetDateTime(),
                item.pickupNumber() == null ? null : String.format("%03d", item.pickupNumber()),
                item.totalAmount(), item.itemSummary());
    }
}
