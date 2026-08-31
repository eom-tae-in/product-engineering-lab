package kr.savepick.order.api;

import java.time.ZoneId;
import java.util.List;
import kr.savepick.order.application.OrderQueryService.AdminOrderDetail;
import kr.savepick.order.domain.Order;

/** API-113·API-114. */
public record AdminOrderDetailResponse(
        Long orderId, String orderNo, String status, String pickupNumber, AdminOrderCustomerResponse customer,
        List<OrderDetailItemResponse> items, int totalAmount, List<PaymentAttemptResponse> paymentAttempts,
        List<OrderStatusHistoryResponse> statusHistory, List<String> availableActions) {

    public static AdminOrderDetailResponse from(AdminOrderDetail detail, ZoneId zone) {
        Order order = detail.order();
        return new AdminOrderDetailResponse(
                order.getId(), order.getOrderNo(), order.getStatus().name(),
                order.getPickupNumber() == null ? null : String.format("%03d", order.getPickupNumber()),
                new AdminOrderCustomerResponse(order.getContactName(), order.getContactPhone()),
                detail.items().stream().map(OrderDetailItemResponse::from).toList(),
                order.getTotalAmount(),
                detail.paymentAttempts().stream().map(a -> PaymentAttemptResponse.from(a, zone)).toList(),
                detail.history().stream().map(h -> OrderStatusHistoryResponse.from(h, zone)).toList(),
                detail.availableActions());
    }
}
