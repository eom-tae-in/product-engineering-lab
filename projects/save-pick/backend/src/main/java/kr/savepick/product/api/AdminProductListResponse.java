package kr.savepick.product.api;

import java.time.OffsetDateTime;
import java.util.List;
import kr.savepick.common.response.PageMeta;

/** API-102. */
public record AdminProductListResponse(OffsetDateTime serverTime, List<AdminProductListItemResponse> items, PageMeta page) {
}
