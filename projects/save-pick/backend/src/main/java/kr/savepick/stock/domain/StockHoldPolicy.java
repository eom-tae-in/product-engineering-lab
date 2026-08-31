package kr.savepick.stock.domain;

/**
 * BR-006, BR-027 — 요청 수량이 판매 가능 수량을 넘는지 판정한다. 부분 선점을 허용하지 않으므로
 * (G7) 호출자가 여러 품목 각각에 대해 이 판정을 모은 뒤 하나라도 부족하면 전체를 취소해야 한다.
 */
public final class StockHoldPolicy {

    private StockHoldPolicy() {
    }

    public static boolean canHold(StockQuantities quantities, int requestedQuantity) {
        return quantities.available() >= requestedQuantity;
    }

    public static int shortage(StockQuantities quantities, int requestedQuantity) {
        return Math.max(0, requestedQuantity - quantities.available());
    }
}
