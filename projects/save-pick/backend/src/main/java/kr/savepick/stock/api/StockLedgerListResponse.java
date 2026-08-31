package kr.savepick.stock.api;

import java.util.List;
import kr.savepick.common.response.PageMeta;

/** API-111. */
public record StockLedgerListResponse(List<StockLedgerItemResponse> items, PageMeta page) {
}
