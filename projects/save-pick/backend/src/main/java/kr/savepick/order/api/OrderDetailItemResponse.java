package kr.savepick.order.api;

import kr.savepick.order.domain.OrderItem;

/** API-024·API-114 공용. */
public record OrderDetailItemResponse(Long productId, String name, int quantity, int unitPrice, int lineAmount) {

    public static OrderDetailItemResponse from(OrderItem item) {
        return new OrderDetailItemResponse(item.getProductId(), item.getProductName(), item.getQuantity(), item.getUnitPrice(), item.getLineAmount());
    }
}
