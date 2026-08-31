package kr.savepick.order.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import kr.savepick.order.application.PickupSlotAssignService.AssignResult;

/** API-021. */
public record AssignPickupSlotResponse(
        Long orderId, Long pickupSlotId, OffsetDateTime pickupStartAt, OffsetDateTime pickupEndAt, long holdRemainingSeconds) {

    public static AssignPickupSlotResponse from(AssignResult result, ZoneId zone) {
        return new AssignPickupSlotResponse(
                result.order().getId(), result.assignment().slotId(),
                result.assignment().startAt().atZone(zone).toOffsetDateTime(),
                result.assignment().endAt().atZone(zone).toOffsetDateTime(),
                result.holdRemainingSeconds());
    }
}
