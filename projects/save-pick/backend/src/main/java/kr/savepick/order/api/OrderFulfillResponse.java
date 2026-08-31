package kr.savepick.order.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import kr.savepick.order.application.OrderFulfillService.FulfillResult;

/** API-115·API-116. 둘 다 재고를 바꾸지 않는다(stockChanged는 항상 false). */
public record OrderFulfillResponse(Long orderId, String status, OffsetDateTime occurredAt, boolean stockChanged) {

    public static OrderFulfillResponse from(FulfillResult result, ZoneId zone) {
        return new OrderFulfillResponse(result.orderId(), result.status().name(), result.occurredAt().atZone(zone).toOffsetDateTime(), false);
    }
}
