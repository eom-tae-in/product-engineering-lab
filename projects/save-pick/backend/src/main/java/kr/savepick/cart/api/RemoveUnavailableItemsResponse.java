package kr.savepick.cart.api;

import java.util.List;

/** API-016. */
public record RemoveUnavailableItemsResponse(
        List<Long> removedCartItemIds, int remainingItemCount, int totalAmount, boolean orderable) {
}
