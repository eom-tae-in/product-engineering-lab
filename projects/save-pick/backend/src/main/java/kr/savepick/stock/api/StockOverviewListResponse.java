package kr.savepick.stock.api;

import java.time.OffsetDateTime;
import java.util.List;
import kr.savepick.common.response.PageMeta;

/** API-110. */
public record StockOverviewListResponse(OffsetDateTime serverTime, List<StockOverviewItemResponse> items, PageMeta page) {
}
