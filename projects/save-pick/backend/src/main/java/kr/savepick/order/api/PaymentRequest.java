package kr.savepick.order.api;

import jakarta.validation.constraints.NotNull;

/** API-022. */
public record PaymentRequest(@NotNull Integer amount) {
}
