package kr.savepick.product.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import kr.savepick.product.application.ProductQueryService.AdminListItem;

/** API-102. */
public record AdminProductListItemResponse(
        Long productId, String name, String status, int originalPrice,
        int currentDiscountRate, int currentPrice, Integer nextDiscountRate, OffsetDateTime nextDiscountAt,
        OffsetDateTime closingAt, int totalQuantity, int availableQuantity) {

    public static AdminProductListItemResponse from(AdminListItem item, ZoneId zone) {
        return new AdminProductListItemResponse(
                item.productId(), item.name(), item.status().name(), item.originalPrice(),
                item.currentDiscountRate(), item.currentPrice(), item.nextDiscountRate(),
                item.nextDiscountAt() == null ? null : item.nextDiscountAt().atZone(zone).toOffsetDateTime(),
                item.closingAt().atZone(zone).toOffsetDateTime(), item.totalQuantity(), item.availableQuantity());
    }
}
