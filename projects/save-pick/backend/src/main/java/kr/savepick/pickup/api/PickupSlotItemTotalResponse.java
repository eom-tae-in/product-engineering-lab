package kr.savepick.pickup.api;

import kr.savepick.pickup.infrastructure.PickupOrderReadDao.ItemTotalRow;

/** API-118. */
public record PickupSlotItemTotalResponse(Long productId, String name, int quantity) {

    public static PickupSlotItemTotalResponse from(ItemTotalRow row) {
        return new PickupSlotItemTotalResponse(row.productId(), row.productName(), row.quantity());
    }
}
