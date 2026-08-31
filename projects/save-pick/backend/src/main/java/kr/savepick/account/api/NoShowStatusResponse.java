package kr.savepick.account.api;

import java.time.OffsetDateTime;
import java.util.List;
import kr.savepick.account.domain.OrderPermission;

public record NoShowStatusResponse(
        int recentNoShowCount,
        int windowDays,
        OrderPermission orderPermission,
        OffsetDateTime restrictedUntil,
        List<NoShowOrderItem> noShowOrders) {

    public record NoShowOrderItem(String orderNo, OffsetDateTime noShowAt) {
    }
}
