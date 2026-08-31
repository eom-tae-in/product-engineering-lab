package kr.savepick.stock.api;

import kr.savepick.stock.domain.StockQuantities;

/** API-109 응답의 before/after 조각. */
public record StockSnapshotResponse(int totalQuantity, int availableQuantity, int heldQuantity, int confirmedQuantity) {

    public static StockSnapshotResponse from(StockQuantities quantities) {
        return new StockSnapshotResponse(quantities.total(), quantities.available(), quantities.held(), quantities.confirmed());
    }
}
