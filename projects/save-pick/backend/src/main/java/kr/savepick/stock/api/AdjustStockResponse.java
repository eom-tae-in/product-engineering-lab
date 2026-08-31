package kr.savepick.stock.api;

import java.time.OffsetDateTime;
import kr.savepick.stock.application.StockAdjustService;

/** API-109. */
public record AdjustStockResponse(
        Long productId,
        StockSnapshotResponse before,
        StockSnapshotResponse after,
        int minimumSettableQuantity,
        OffsetDateTime changedAt) {

    public static AdjustStockResponse of(Long productId, StockAdjustService.AdjustResult result, java.time.ZoneId zone) {
        return new AdjustStockResponse(
                productId,
                StockSnapshotResponse.from(result.before()),
                StockSnapshotResponse.from(result.after()),
                result.minimumSettableQuantity(),
                result.changedAt().atZone(zone).toOffsetDateTime());
    }
}
