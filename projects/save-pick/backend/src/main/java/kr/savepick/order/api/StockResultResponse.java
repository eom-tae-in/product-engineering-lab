package kr.savepick.order.api;

import kr.savepick.order.application.OrderCancelService.StockResult;

/** API-025·API-117 공용. */
public record StockResultResponse(Long productId, int quantity, boolean restored, String reason, String note) {

    public static StockResultResponse from(StockResult result) {
        return new StockResultResponse(result.productId(), result.quantity(), result.restored(), result.reason().name(), result.note());
    }
}
