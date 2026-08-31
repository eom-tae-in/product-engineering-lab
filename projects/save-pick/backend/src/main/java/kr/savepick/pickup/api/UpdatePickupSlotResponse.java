package kr.savepick.pickup.api;

import kr.savepick.pickup.application.PickupSlotAdminService.UpdateResult;

/** API-119. */
public record UpdatePickupSlotResponse(
        Long slotId, int capacity, int reservedCount, boolean blocked, boolean overCapacity, int keptOrderCount) {

    public static UpdatePickupSlotResponse from(UpdateResult result) {
        return new UpdatePickupSlotResponse(
                result.slot().getId(), result.slot().getCapacity(), result.slot().getReservedCount(),
                result.slot().isBlocked(), result.overCapacity(), result.keptOrderCount());
    }
}
