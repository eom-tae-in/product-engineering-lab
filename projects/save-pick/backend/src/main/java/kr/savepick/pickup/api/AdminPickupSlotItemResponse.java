package kr.savepick.pickup.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import kr.savepick.pickup.application.PickupSlotQueryService.AdminSlotItem;

/** API-118. */
public record AdminPickupSlotItemResponse(
        Long slotId, OffsetDateTime startAt, OffsetDateTime endAt, int capacity, int reservedCount,
        boolean full, boolean blocked, boolean reservationClosed, List<PickupSlotItemTotalResponse> itemTotals) {

    public static AdminPickupSlotItemResponse from(AdminSlotItem item, ZoneId zone) {
        return new AdminPickupSlotItemResponse(
                item.slotId(),
                item.startAt().atZone(zone).toOffsetDateTime(),
                item.endAt().atZone(zone).toOffsetDateTime(),
                item.capacity(), item.reservedCount(), item.full(), item.blocked(), item.reservationClosed(),
                item.itemTotals().stream().map(PickupSlotItemTotalResponse::from).toList());
    }
}
