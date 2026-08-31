package kr.savepick.cart.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** API-013 (11-api-spec.md §3, BR-009). */
public record AddCartItemRequest(
        @NotNull Long productId,
        @Min(1) int quantity) {
}
