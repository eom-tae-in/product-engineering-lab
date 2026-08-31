package kr.savepick.cart.api;

import jakarta.validation.constraints.Min;

/** API-014. {@code quantity = 0}이면 품목을 삭제한다(FR-017). */
public record ChangeCartItemQuantityRequest(
        @Min(0) int quantity) {
}
