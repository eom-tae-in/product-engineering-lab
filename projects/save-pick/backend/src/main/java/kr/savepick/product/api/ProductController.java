package kr.savepick.product.api;

import java.time.ZoneId;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.product.application.ProductQueryService;
import kr.savepick.product.application.ProductQueryService.ProductListResult;
import kr.savepick.product.application.ProductSort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-010·011 상품 목록·상세 조회 (11-api-spec.md §2). 비로그인 포함 전체 공개 —
 * SecurityConfig에서 permitAll로 열어둔다.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductQueryService productQueryService;

    public ProductController(ProductQueryService productQueryService) {
        this.productQueryService = productQueryService;
    }

    /** API-010. FR-010~012, FR-014, FR-015, FR-034. */
    @GetMapping
    public ProductListResponse list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "CLOSING_SOON") String sort,
            @RequestParam(defaultValue = "false") boolean hideSoldOut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ProductSort sortOption = parseSort(sort);
        if (size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "size는 100을 넘을 수 없습니다.");
        }

        ProductListResult result = productQueryService.getPublicList(keyword, sortOption, hideSoldOut, page, size);
        return new ProductListResponse(
                result.serverTime().atZone(ZONE).toOffsetDateTime(),
                result.items().stream().map(item -> ProductListItemResponse.from(item, ZONE)).toList(),
                new kr.savepick.common.response.PageMeta(result.number(), result.size(), result.totalElements()));
    }

    /** API-011. FR-013~015, FR-034. */
    @GetMapping("/{productId}")
    public ProductDetailResponse detail(@PathVariable Long productId) {
        return ProductDetailResponse.from(productQueryService.getPublicDetail(productId), ZONE);
    }

    private ProductSort parseSort(String sort) {
        try {
            return ProductSort.valueOf(sort);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "sort 값이 올바르지 않습니다.");
        }
    }
}
