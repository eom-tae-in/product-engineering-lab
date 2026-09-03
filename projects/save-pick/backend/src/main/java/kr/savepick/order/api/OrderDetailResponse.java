package kr.savepick.order.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import kr.savepick.order.application.OrderQueryService.CustomerOrderDetail;
import kr.savepick.order.domain.Order;

/** API-024 (11-api-spec.md §5). */
public record OrderDetailResponse(
        OffsetDateTime serverTime, Long orderId, String orderNo, String status, OffsetDateTime orderedAt,
        List<OrderDetailItemResponse> items, int totalAmount, String pickupNumber, OffsetDateTime pickupStartAt,
        OffsetDateTime pickupEndAt, OffsetDateTime noShowDueAt, boolean cancelable, OffsetDateTime cancelableUntil,
        String cancelUnavailableReason, String canceledBy, String cancelReason, OrderStoreResponse store,
        List<OrderStatusHistoryResponse> statusHistory, OffsetDateTime noShowAt, Boolean refunded) {

    public static OrderDetailResponse from(CustomerOrderDetail detail, ZoneId zone) {
        Order order = detail.order();
        return new OrderDetailResponse(
                detail.serverTime().atZone(zone).toOffsetDateTime(),
                order.getId(), order.getOrderNo(), order.getStatus().name(), order.getCreatedAt().atZone(zone).toOffsetDateTime(),
                detail.items().stream().map(item -> OrderDetailItemResponse.from(item, zone)).toList(),
                order.getTotalAmount(),
                order.getPickupNumber() == null ? null : String.format("%03d", order.getPickupNumber()),
                offset(detail.pickupStartAt(), zone), offset(detail.pickupEndAt(), zone), offset(order.getNoShowDueAt(), zone),
                detail.cancelable(), offset(order.getCancelableUntil(), zone),
                detail.cancelUnavailableReason() == null ? null : detail.cancelUnavailableReason().name(),
                order.getCanceledBy(), order.getCancelReason(), OrderStoreResponse.from(detail.store()),
                detail.history().stream().map(h -> OrderStatusHistoryResponse.from(h, zone)).toList(),
                offset(order.getNoShowAt(), zone), order.getNoShowAt() == null ? null : Boolean.FALSE);
    }

    private static OffsetDateTime offset(java.time.LocalDateTime value, ZoneId zone) {
        return value == null ? null : value.atZone(zone).toOffsetDateTime();
    }
}
