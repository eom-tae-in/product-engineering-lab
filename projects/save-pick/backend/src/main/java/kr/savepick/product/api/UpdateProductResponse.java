package kr.savepick.product.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import kr.savepick.product.application.ProductUpdateService.UpdateResult;

/** API-105. */
public record UpdateProductResponse(
        Long productId, int originalPrice, OffsetDateTime closingAt, int maxOrderQuantity,
        List<String> changedFields, int affectedConfirmedOrderCount, OffsetDateTime updatedAt) {

    public static UpdateProductResponse from(UpdateResult result, ZoneId zone) {
        var product = result.product();
        return new UpdateProductResponse(
                product.getId(), product.getOriginalPrice(), product.getClosingAt().atZone(zone).toOffsetDateTime(),
                product.getMaxOrderQuantity(), result.changedFields(), result.affectedConfirmedOrderCount(),
                product.getUpdatedAt().atZone(zone).toOffsetDateTime());
    }
}
