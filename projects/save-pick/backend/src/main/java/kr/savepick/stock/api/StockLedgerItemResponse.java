package kr.savepick.stock.api;

import java.time.OffsetDateTime;
import kr.savepick.stock.application.StockQueryService;

/**
 * API-111.
 *
 * <p>{@code orderId}는 SC-106이 관련 주문을 SC-110(관리자 주문 상세, {@code /admin/orders/{id}})으로
 * 열기 위해 쓴다. {@code orderNo}만으로는 라우트를 만들 수 없어 함께 내려준다.
 */
public record StockLedgerItemResponse(
        Long ledgerId, String reason, Long orderId, String orderNo,
        int deltaTotal, int deltaHeld, int deltaConfirmed, int deltaDiscarded,
        int afterTotal, int afterAvailable, int afterHeld, int afterConfirmed,
        String actorType, String note, OffsetDateTime occurredAt) {

    public static StockLedgerItemResponse from(StockQueryService.LedgerItem item, java.time.ZoneId zone) {
        return new StockLedgerItemResponse(
                item.ledgerId(), item.reason().name(), item.orderId(), item.orderNo(),
                item.deltaTotal(), item.deltaHeld(), item.deltaConfirmed(), item.deltaDiscarded(),
                item.afterTotal(), item.afterAvailable(), item.afterHeld(), item.afterConfirmed(),
                item.actorType().name(), item.note(), item.occurredAt().atZone(zone).toOffsetDateTime());
    }
}
