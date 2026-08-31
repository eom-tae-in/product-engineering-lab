package kr.savepick.stock.domain;

/**
 * BR-006 — 재고를 구성하는 4개 값의 값 객체. {@code available}은 항상
 * {@code total - held - confirmed}로 파생된다 (product_stocks.available_quantity 생성 컬럼과 동일한 식).
 */
public record StockQuantities(int total, int held, int confirmed, int discarded) {

    public static StockQuantities zero() {
        return new StockQuantities(0, 0, 0, 0);
    }

    public static StockQuantities of(ProductStock stock) {
        return new StockQuantities(
                stock.getTotalQuantity(), stock.getHeldQuantity(), stock.getConfirmedQuantity(), stock.getDiscardedQuantity());
    }

    public int available() {
        return total - held - confirmed;
    }

    public StockQuantities applyDelta(int deltaTotal, int deltaHeld, int deltaConfirmed, int deltaDiscarded) {
        return new StockQuantities(total + deltaTotal, held + deltaHeld, confirmed + deltaConfirmed, discarded + deltaDiscarded);
    }

    /** 13번 §2.1 — 만료됐지만 아직 회수되지 않은 선점을 표시용으로 보정한다. 쓰기를 하지 않는다. */
    public StockQuantities withExpiredHeldExcluded(int expiredHeldQuantity) {
        if (expiredHeldQuantity <= 0) {
            return this;
        }
        return new StockQuantities(total, held - expiredHeldQuantity, confirmed, discarded);
    }
}
