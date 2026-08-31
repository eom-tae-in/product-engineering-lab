package kr.savepick.order.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import kr.savepick.order.application.OrderQueryService.AdminOrderListItem;

/** API-112. */
public record AdminOrderListItemResponse(
        Long orderId, String orderNo, String pickupNumber, String customerName, String status,
        OffsetDateTime pickupStartAt, OffsetDateTime pickupEndAt, OffsetDateTime noShowDueAt, int totalAmount, int itemCount) {

    public static AdminOrderListItemResponse from(AdminOrderListItem item, ZoneId zone) {
        return new AdminOrderListItemResponse(
                item.orderId(), item.orderNo(), item.pickupNumber() == null ? null : String.format("%03d", item.pickupNumber()),
                item.customerName(), item.status().name(),
                item.pickupStartAt() == null ? null : item.pickupStartAt().atZone(zone).toOffsetDateTime(),
                item.pickupEndAt() == null ? null : item.pickupEndAt().atZone(zone).toOffsetDateTime(),
                item.noShowDueAt() == null ? null : item.noShowDueAt().atZone(zone).toOffsetDateTime(),
                item.totalAmount(), item.itemCount());
    }
}
