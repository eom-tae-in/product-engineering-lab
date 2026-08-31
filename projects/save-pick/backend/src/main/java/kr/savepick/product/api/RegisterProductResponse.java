package kr.savepick.product.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import kr.savepick.product.domain.Product;

/** API-103. */
public record RegisterProductResponse(
        Long productId, String status, String name, int originalPrice, OffsetDateTime closingAt, int maxOrderQuantity) {

    public static RegisterProductResponse from(Product product, ZoneId zone) {
        return new RegisterProductResponse(
                product.getId(), product.getStatus().name(), product.getName(), product.getOriginalPrice(),
                product.getClosingAt().atZone(zone).toOffsetDateTime(), product.getMaxOrderQuantity());
    }
}
