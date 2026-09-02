package kr.savepick.order.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import kr.savepick.order.application.OrderQueryService.AdminOrderDetail;
import kr.savepick.order.domain.Order;

/**
 * API-113·API-114.
 *
 * `pickupStartAt`·`pickupEndAt`은 06번 SC-109(픽업 번호 조회)·SC-110(주문 상세, 관리자)의
 * "픽업 날짜·시간대" 표시에 쓴다. 시각은 주문이 아니라 지정된 픽업 시간대(PickupSlot)에
 * 있으므로 아직 시간대를 고르지 않은 PENDING 주문에서는 null이다 — API-112(목록)의
 * 같은 필드와 같은 규칙이다.
 */
public record AdminOrderDetailResponse(
        Long orderId, String orderNo, String status, String pickupNumber,
        OffsetDateTime pickupStartAt, OffsetDateTime pickupEndAt, AdminOrderCustomerResponse customer,
        List<OrderDetailItemResponse> items, int totalAmount, List<PaymentAttemptResponse> paymentAttempts,
        List<OrderStatusHistoryResponse> statusHistory, List<String> availableActions) {

    public static AdminOrderDetailResponse from(AdminOrderDetail detail, ZoneId zone) {
        Order order = detail.order();
        return new AdminOrderDetailResponse(
                order.getId(), order.getOrderNo(), order.getStatus().name(),
                order.getPickupNumber() == null ? null : String.format("%03d", order.getPickupNumber()),
                detail.pickupStartAt() == null ? null : detail.pickupStartAt().atZone(zone).toOffsetDateTime(),
                detail.pickupEndAt() == null ? null : detail.pickupEndAt().atZone(zone).toOffsetDateTime(),
                new AdminOrderCustomerResponse(order.getContactName(), order.getContactPhone()),
                detail.items().stream().map(OrderDetailItemResponse::from).toList(),
                order.getTotalAmount(),
                detail.paymentAttempts().stream().map(a -> PaymentAttemptResponse.from(a, zone)).toList(),
                detail.history().stream().map(h -> OrderStatusHistoryResponse.from(h, zone)).toList(),
                detail.availableActions());
    }
}
