package kr.savepick.stock.domain;

/**
 * BR-025 — 총 재고는 (선점 중 + 확정 판매) 미만으로 줄일 수 없다.
 */
public final class StockReductionPolicy {

    private StockReductionPolicy() {
    }

    public static int minimumSettableQuantity(StockQuantities quantities) {
        return quantities.held() + quantities.confirmed();
    }

    public static boolean isTargetAllowed(int targetTotal, StockQuantities quantities) {
        return targetTotal >= minimumSettableQuantity(quantities);
    }
}
