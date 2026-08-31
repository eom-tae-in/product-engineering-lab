package kr.savepick.order.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import kr.savepick.order.application.OrderCancelService.CancelResult;

/** API-025·API-117 공용. */
public record OrderCancelResponse(
        Long orderId, String status, OffsetDateTime canceledAt, String canceledBy, String cancelReason,
        boolean slotReleased, List<StockResultResponse> stockResults) {

    public static OrderCancelResponse from(CancelResult result, ZoneId zone) {
        return new OrderCancelResponse(
                result.orderId(), "CANCELED", result.canceledAt().atZone(zone).toOffsetDateTime(), result.canceledBy(),
                result.cancelReason(), result.slotReleased(), result.stockResults().stream().map(StockResultResponse::from).toList());
    }
}
