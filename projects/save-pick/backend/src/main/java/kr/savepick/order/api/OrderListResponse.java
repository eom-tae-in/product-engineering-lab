package kr.savepick.order.api;

import java.time.ZoneId;
import java.util.List;
import kr.savepick.common.response.PageMeta;
import kr.savepick.order.application.OrderQueryService.CustomerOrderListPage;

/** API-023. */
public record OrderListResponse(List<OrderListItemResponse> items, PageMeta page) {

    public static OrderListResponse from(CustomerOrderListPage result, ZoneId zone) {
        List<OrderListItemResponse> items = result.items().stream().map(item -> OrderListItemResponse.from(item, zone)).toList();
        return new OrderListResponse(items, new PageMeta(result.number(), result.size(), result.totalElements()));
    }
}
