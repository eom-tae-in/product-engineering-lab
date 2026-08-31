package kr.savepick.cart.api;

/** API-014 — 수량이 0이 아니어서 삭제되지 않은 경우의 200 응답. */
public record ChangeCartItemQuantityResponse(Long cartItemId, int quantity, int lineAmount, int totalAmount) {
}
