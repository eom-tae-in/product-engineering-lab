package kr.savepick.order.api;

import java.time.ZoneId;
import java.util.List;
import kr.savepick.common.response.PageMeta;
import kr.savepick.order.application.OrderQueryService.AdminOrderListPage;

/** API-112. */
public record AdminOrderListResponse(List<AdminOrderListItemResponse> items, PageMeta page) {

    public static AdminOrderListResponse from(AdminOrderListPage result, ZoneId zone) {
        List<AdminOrderListItemResponse> items = result.items().stream().map(item -> AdminOrderListItemResponse.from(item, zone)).toList();
        return new AdminOrderListResponse(items, new PageMeta(result.number(), result.size(), result.totalElements()));
    }
}
