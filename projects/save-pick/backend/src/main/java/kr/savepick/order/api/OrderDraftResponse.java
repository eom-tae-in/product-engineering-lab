package kr.savepick.order.api;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import kr.savepick.order.application.OrderDraftService.DraftResult;

/** API-017 (11-api-spec.md §4). */
public record OrderDraftResponse(
        Long orderId, String orderNo, String status, OffsetDateTime serverTime, OffsetDateTime holdExpiresAt,
        long holdRemainingSeconds, int paymentAttemptRemaining, int totalAmount, List<OrderDraftItemResponse> items,
        OffsetDateTime earliestClosingAt) {

    public static OrderDraftResponse of(DraftResult result, int paymentMaxAttempts, ZoneId zone) {
        long remaining = Math.max(
                0, Duration.between(result.serverTime(), result.order().getHoldExpiresAt()).toSeconds());
        return new OrderDraftResponse(
                result.order().getId(),
                result.order().getOrderNo(),
                result.order().getStatus().name(),
                result.serverTime().atZone(zone).toOffsetDateTime(),
                result.order().getHoldExpiresAt().atZone(zone).toOffsetDateTime(),
                remaining,
                Math.max(0, paymentMaxAttempts - result.order().getPaymentAttemptCount()),
                result.order().getTotalAmount(),
                result.items().stream().map(item -> OrderDraftItemResponse.from(item, zone)).toList(),
                result.earliestClosingAt().atZone(zone).toOffsetDateTime());
    }
}
