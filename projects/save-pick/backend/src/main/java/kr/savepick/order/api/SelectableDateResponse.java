package kr.savepick.order.api;

import java.time.LocalDate;
import kr.savepick.pickup.application.PickupSlotQueryService.SelectableDate;

/** API-020. */
public record SelectableDateResponse(LocalDate date, String label, boolean selectable, String unselectableReason) {

    public static SelectableDateResponse from(SelectableDate date) {
        return new SelectableDateResponse(
                date.date(), date.label(), date.selectable(),
                date.unselectableReason() == null ? null : date.unselectableReason().name());
    }
}
