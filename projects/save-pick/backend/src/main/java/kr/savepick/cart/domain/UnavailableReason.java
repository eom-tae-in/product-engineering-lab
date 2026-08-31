package kr.savepick.cart.domain;

/**
 * 11-api-spec.md API-012 — 장바구니 품목이 지금 구매 불가한 사유. 문자 그대로
 * 응답 필드 {@code unavailableReason}의 값이 된다 (14-project-structure.md §7.1 열거형 규칙).
 */
public enum UnavailableReason {
    OUT_OF_STOCK,
    PRODUCT_CLOSED,
    PRODUCT_NOT_ON_SALE
}
