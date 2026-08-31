package kr.savepick.order.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import kr.savepick.order.domain.OrderItem;

/** API-017. */
public record OrderDraftItemResponse(
        Long productId, String name, int quantity, int originalUnitPrice, int discountRate, int unitPrice,
        int lineAmount, OffsetDateTime productClosingAt) {

    public static OrderDraftItemResponse from(OrderItem item, ZoneId zone) {
        return new OrderDraftItemResponse(
                item.getProductId(), item.getProductName(), item.getQuantity(), item.getOriginalUnitPrice(),
                item.getDiscountRate(), item.getUnitPrice(), item.getLineAmount(),
                item.getProductClosingAt().atZone(zone).toOffsetDateTime());
    }
}
