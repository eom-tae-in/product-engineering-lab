package kr.savepick.stock.api;

import java.time.OffsetDateTime;
import kr.savepick.stock.application.StockQueryService;

/** API-111. */
public record StockLedgerItemResponse(
        Long ledgerId, String reason, String orderNo,
        int deltaTotal, int deltaHeld, int deltaConfirmed, int deltaDiscarded,
        int afterTotal, int afterAvailable, int afterHeld, int afterConfirmed,
        String actorType, String note, OffsetDateTime occurredAt) {

    public static StockLedgerItemResponse from(StockQueryService.LedgerItem item, java.time.ZoneId zone) {
        return new StockLedgerItemResponse(
                item.ledgerId(), item.reason().name(), item.orderNo(),
                item.deltaTotal(), item.deltaHeld(), item.deltaConfirmed(), item.deltaDiscarded(),
                item.afterTotal(), item.afterAvailable(), item.afterHeld(), item.afterConfirmed(),
                item.actorType().name(), item.note(), item.occurredAt().atZone(zone).toOffsetDateTime());
    }
}
