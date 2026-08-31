package kr.savepick.order.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import kr.savepick.pickup.application.PickupSlotQueryService.SlotView;

/** API-020. */
public record SelectableSlotResponse(
        Long slotId, java.time.LocalDate date, OffsetDateTime startAt, OffsetDateTime endAt, int capacity,
        int reservedCount, boolean selectable, String unselectableReason) {

    public static SelectableSlotResponse from(SlotView view, ZoneId zone) {
        return new SelectableSlotResponse(
                view.slotId(), view.date(), view.startAt().atZone(zone).toOffsetDateTime(),
                view.endAt().atZone(zone).toOffsetDateTime(), view.capacity(), view.reservedCount(), view.selectable(),
                view.unselectableReason() == null ? null : view.unselectableReason().name());
    }
}
