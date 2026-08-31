package kr.savepick.product.api;

import java.util.List;
import kr.savepick.common.response.PageMeta;

/** API-107. */
public record ProductChangeLogListResponse(List<ProductChangeLogItemResponse> items, PageMeta page) {
}
