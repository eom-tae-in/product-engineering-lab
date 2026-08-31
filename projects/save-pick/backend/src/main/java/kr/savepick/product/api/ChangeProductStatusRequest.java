package kr.savepick.product.api;

import jakarta.validation.constraints.NotBlank;

/** API-106. */
public record ChangeProductStatusRequest(@NotBlank String status) {
}
