package kr.savepick.order.api;

import jakarta.validation.constraints.NotNull;

/** API-021. */
public record AssignPickupSlotRequest(@NotNull Long slotId) {
}
