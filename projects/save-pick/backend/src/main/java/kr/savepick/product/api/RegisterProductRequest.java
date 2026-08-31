package kr.savepick.product.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/** API-103. */
public record RegisterProductRequest(
        @NotBlank String name,
        String description,
        @NotBlank String saleUnit,
        @NotNull @Min(100) Integer originalPrice,
        @NotNull OffsetDateTime closingAt,
        @Min(1) Short maxOrderQuantity) {
}
