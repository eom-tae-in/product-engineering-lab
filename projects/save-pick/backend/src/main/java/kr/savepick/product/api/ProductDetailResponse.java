package kr.savepick.product.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import kr.savepick.product.application.ProductQueryService.PublicDetailResult;

/** API-011. */
public record ProductDetailResponse(
        OffsetDateTime serverTime, Long productId, String name, String description, String saleUnit,
        int originalPrice, int discountRate, int discountPrice, int availableQuantity, boolean lowStock,
        boolean soldOut, int maxOrderQuantity, OffsetDateTime closingAt, boolean purchasable) {

    public static ProductDetailResponse from(PublicDetailResult result, ZoneId zone) {
        var product = result.product();
        int available = result.quantities().available();
        return new ProductDetailResponse(
                result.serverTime().atZone(zone).toOffsetDateTime(),
                product.getId(), product.getName(), product.getDescription(), product.getSaleUnit(),
                product.getOriginalPrice(), result.discount().rate(), result.discount().price(),
                available, available <= 5, available <= 0,
                product.getMaxOrderQuantity(), product.getClosingAt().atZone(zone).toOffsetDateTime(),
                result.purchasable());
    }
}
