package kr.savepick.stock.api;

import kr.savepick.stock.application.StockQueryService;

/** API-110. */
public record StockOverviewItemResponse(
        Long productId, String name, String status,
        int totalQuantity, int availableQuantity, int heldQuantity, int confirmedQuantity,
        int discardedQuantity, boolean consistent) {

    public static StockOverviewItemResponse from(StockQueryService.OverviewItem item) {
        return new StockOverviewItemResponse(
                item.productId(), item.name(), item.status() == null ? null : item.status().name(),
                item.totalQuantity(), item.availableQuantity(), item.heldQuantity(), item.confirmedQuantity(),
                item.discardedQuantity(), item.consistent());
    }
}
