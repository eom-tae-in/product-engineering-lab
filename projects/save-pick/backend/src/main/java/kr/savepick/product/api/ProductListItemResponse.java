package kr.savepick.product.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import kr.savepick.product.application.ProductQueryService.PublicListItem;

/** API-010. */
public record ProductListItemResponse(
        Long productId, String name, String saleUnit, int originalPrice,
        int discountRate, int discountPrice, int availableQuantity, boolean lowStock, boolean soldOut,
        OffsetDateTime closingAt, OffsetDateTime nextDiscountAt) {

    public static ProductListItemResponse from(PublicListItem item, ZoneId zone) {
        return new ProductListItemResponse(
                item.productId(), item.name(), item.saleUnit(), item.originalPrice(),
                item.discountRate(), item.discountPrice(), item.availableQuantity(), item.lowStock(), item.soldOut(),
                item.closingAt().atZone(zone).toOffsetDateTime(),
                item.nextDiscountAt() == null ? null : item.nextDiscountAt().atZone(zone).toOffsetDateTime());
    }
}
