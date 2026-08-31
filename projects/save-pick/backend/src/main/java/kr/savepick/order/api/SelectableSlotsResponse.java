package kr.savepick.order.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import kr.savepick.pickup.application.PickupSlotQueryService.SelectableSlotsResult;

/** API-020. */
public record SelectableSlotsResponse(
        OffsetDateTime serverTime, List<SelectableDateResponse> selectableDates, List<SelectableSlotResponse> slots) {

    public static SelectableSlotsResponse from(SelectableSlotsResult result, ZoneId zone) {
        return new SelectableSlotsResponse(
                result.serverTime().atZone(zone).toOffsetDateTime(),
                result.selectableDates().stream().map(SelectableDateResponse::from).toList(),
                result.slots().stream().map(slot -> SelectableSlotResponse.from(slot, zone)).toList());
    }
}
