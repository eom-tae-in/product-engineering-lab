package kr.savepick.stock.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** API-109. */
public record AdjustStockRequest(
        @NotNull @Min(0) Integer totalQuantity,
        @Size(max = 200) String note) {
}
