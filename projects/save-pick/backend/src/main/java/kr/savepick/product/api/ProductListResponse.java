package kr.savepick.product.api;

import java.time.OffsetDateTime;
import java.util.List;
import kr.savepick.common.response.PageMeta;

/** API-010. */
public record ProductListResponse(OffsetDateTime serverTime, List<ProductListItemResponse> items, PageMeta page) {
}
