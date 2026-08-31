package kr.savepick.product.api;

import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;

/** API-105. 부분 수정 — null인 필드는 변경하지 않는다. */
public record UpdateProductRequest(
        String name,
        String description,
        String saleUnit,
        @Min(100) Integer originalPrice,
        OffsetDateTime closingAt,
        @Min(1) Short maxOrderQuantity,
        boolean confirmEarlierClosing) {
}
