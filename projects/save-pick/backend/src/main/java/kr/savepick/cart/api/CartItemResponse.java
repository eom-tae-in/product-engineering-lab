package kr.savepick.cart.api;

import kr.savepick.cart.application.CartValidationService.CartItemEvaluation;

/** API-012 응답의 items[] 원소. */
public record CartItemResponse(
        Long cartItemId, Long productId, String name, int quantity, int addedPrice, int currentPrice,
        boolean priceChanged, int lineAmount, int availableQuantity, int shortage, boolean purchasable,
        String unavailableReason) {

    public static CartItemResponse from(CartItemEvaluation evaluation) {
        return new CartItemResponse(
                evaluation.cartItemId(), evaluation.productId(), evaluation.name(), evaluation.quantity(),
                evaluation.addedPrice(), evaluation.currentPrice(), evaluation.priceChanged(), evaluation.lineAmount(),
                evaluation.availableQuantity(), evaluation.shortage(), evaluation.purchasable(),
                evaluation.unavailableReason() == null ? null : evaluation.unavailableReason().name());
    }
}
