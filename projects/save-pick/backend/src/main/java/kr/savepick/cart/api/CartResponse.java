package kr.savepick.cart.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import kr.savepick.cart.application.CartValidationService.CartView;

/** API-012. */
public record CartResponse(OffsetDateTime serverTime, UUID guestToken, List<CartItemResponse> items, int totalAmount, boolean orderable) {

    public static CartResponse from(CartView view, ZoneId zone) {
        return new CartResponse(
                view.serverTime().atZone(zone).toOffsetDateTime(),
                view.guestToken(),
                view.items().stream().map(CartItemResponse::from).toList(),
                view.totalAmount(),
                view.orderable());
    }
}
