package kr.savepick.pickup.api;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import kr.savepick.pickup.application.PickupSlotQueryService.AdminSlotOverview;

/** API-118. */
public record AdminPickupSlotOverviewResponse(LocalDate date, boolean isHoliday, List<AdminPickupSlotItemResponse> slots) {

    public static AdminPickupSlotOverviewResponse from(AdminSlotOverview overview, ZoneId zone) {
        return new AdminPickupSlotOverviewResponse(
                overview.date(), overview.isHoliday(),
                overview.slots().stream().map(item -> AdminPickupSlotItemResponse.from(item, zone)).toList());
    }
}
