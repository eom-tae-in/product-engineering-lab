package kr.savepick.cart.domain;

/**
 * BR-009 — 상품별 1회 주문 최대 수량(products.max_order_quantity)과 장바구니 전체 품목 수(10개)
 * 한도를 판정한다. 순수 도메인 정책이며 DB를 직접 조회하지 않는다 — 호출자가 현재 수량·품목 수를
 * 인자로 건넨다 (14-project-structure.md §9.1과 같은 패턴).
 */
public final class CartLimitPolicy {

    public static final int MAX_ITEM_COUNT = 10;

    private CartLimitPolicy() {
    }

    /** 품목당 수량이 그 상품의 최대 주문 수량을 넘지 않는지 판정한다. */
    public static boolean isWithinMaxOrderQuantity(int quantity, short maxOrderQuantity) {
        return quantity <= maxOrderQuantity;
    }

    /** 새 품목을 더한 뒤의 전체 품목 수가 10개를 넘지 않는지 판정한다. */
    public static boolean isWithinItemLimit(int itemCountAfterChange) {
        return itemCountAfterChange <= MAX_ITEM_COUNT;
    }
}
