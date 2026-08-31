package kr.savepick.product.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import kr.savepick.product.application.ProductQueryService.AdminDetailResult;
import kr.savepick.stock.domain.StockQuantities;

/** API-104. */
public record AdminProductDetailResponse(
        Long productId, String name, String description, String saleUnit, int originalPrice,
        OffsetDateTime closingAt, int maxOrderQuantity, String status,
        int currentDiscountRate, int currentPrice, Integer nextDiscountRate, OffsetDateTime nextDiscountAt,
        StockSection stock) {

    public static AdminProductDetailResponse from(AdminDetailResult result, ZoneId zone) {
        var product = result.product();
        var discount = result.discount();
        return new AdminProductDetailResponse(
                product.getId(), product.getName(), product.getDescription(), product.getSaleUnit(),
                product.getOriginalPrice(), product.getClosingAt().atZone(zone).toOffsetDateTime(),
                product.getMaxOrderQuantity(), product.getStatus().name(),
                discount.rate(), discount.price(), discount.nextRate(),
                discount.nextAt() == null ? null : discount.nextAt().atZone(zone).toOffsetDateTime(),
                StockSection.from(result.quantities()));
    }

    public record StockSection(int totalQuantity, int availableQuantity, int heldQuantity, int confirmedQuantity, int discardedQuantity) {
        public static StockSection from(StockQuantities quantities) {
            return new StockSection(
                    quantities.total(), quantities.available(), quantities.held(), quantities.confirmed(), quantities.discarded());
        }
    }
}
