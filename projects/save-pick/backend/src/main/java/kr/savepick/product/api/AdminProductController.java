package kr.savepick.product.api;

import jakarta.validation.Valid;
import java.time.ZoneId;
import kr.savepick.account.infrastructure.AuthenticatedPrincipal;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.response.PageMeta;
import kr.savepick.product.application.ProductQueryService;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.application.ProductStatusService;
import kr.savepick.product.application.ProductUpdateService;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-102~108 관리자 상품 관리 (11-api-spec.md §7). 관리자 전용 —
 * SecurityConfig가 /api/admin/**을 ROLE_ADMIN으로 막지만 컨트롤러에도 명시한다.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminProductController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final ProductQueryService productQueryService;
    private final ProductRegisterService productRegisterService;
    private final ProductUpdateService productUpdateService;
    private final ProductStatusService productStatusService;

    public AdminProductController(
            ProductQueryService productQueryService,
            ProductRegisterService productRegisterService,
            ProductUpdateService productUpdateService,
            ProductStatusService productStatusService) {
        this.productQueryService = productQueryService;
        this.productRegisterService = productRegisterService;
        this.productUpdateService = productUpdateService;
        this.productStatusService = productStatusService;
    }

    /** API-102. */
    @GetMapping("/products")
    public AdminProductListResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ProductStatus statusFilter = status == null ? null : parseStatus(status);
        ProductQueryService.AdminListResult result = productQueryService.getAdminList(statusFilter, page, size);
        return new AdminProductListResponse(
                result.serverTime().atZone(ZONE).toOffsetDateTime(),
                result.items().stream().map(item -> AdminProductListItemResponse.from(item, ZONE)).toList(),
                new PageMeta(result.number(), result.size(), result.totalElements()));
    }

    /** API-103. BR-003, BR-009. */
    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterProductResponse register(
            @Valid @RequestBody RegisterProductRequest request,
            @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        short maxOrderQuantity = request.maxOrderQuantity() == null ? 5 : request.maxOrderQuantity();
        Product product = productRegisterService.register(
                request.name(), request.description(), request.saleUnit(), request.originalPrice(),
                request.closingAt().atZoneSameInstant(ZONE).toLocalDateTime(), maxOrderQuantity, principal.memberId());
        return RegisterProductResponse.from(product, ZONE);
    }

    /** API-104. */
    @GetMapping("/products/{productId}")
    public AdminProductDetailResponse detail(@PathVariable Long productId) {
        return AdminProductDetailResponse.from(productQueryService.getAdminDetail(productId), ZONE);
    }

    /** API-105. BR-003, BR-005. */
    @PatchMapping("/products/{productId}")
    public UpdateProductResponse update(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateProductRequest request,
            @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        ProductUpdateService.UpdateResult result = productUpdateService.update(
                productId, request.name(), request.description(), request.saleUnit(), request.originalPrice(),
                request.closingAt() == null ? null : request.closingAt().atZoneSameInstant(ZONE).toLocalDateTime(),
                request.maxOrderQuantity(), request.confirmEarlierClosing(), principal.memberId());
        return UpdateProductResponse.from(result, ZONE);
    }

    /** API-106. BR-025, BR-030. */
    @PatchMapping("/products/{productId}/status")
    public ChangeProductStatusResponse changeStatus(
            @PathVariable Long productId,
            @Valid @RequestBody ChangeProductStatusRequest request,
            @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        ProductStatus target = parseStatus(request.status());
        ProductStatusService.StatusChangeResult result = productStatusService.changeStatus(productId, target, principal.memberId());
        return ChangeProductStatusResponse.from(result, ZONE);
    }

    /** API-107. */
    @GetMapping("/products/{productId}/change-logs")
    public ProductChangeLogListResponse changeLogs(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ProductQueryService.ChangeLogResult result = productQueryService.getChangeLogs(productId, page, size);
        return new ProductChangeLogListResponse(
                result.items().stream().map(item -> ProductChangeLogItemResponse.from(item, ZONE)).toList(),
                new PageMeta(result.number(), result.size(), result.totalElements()));
    }

    /** API-108. BR-004. */
    @GetMapping("/discount-policy")
    public DiscountPolicyResponse discountPolicy() {
        return DiscountPolicyResponse.standard();
    }

    private ProductStatus parseStatus(String status) {
        try {
            return ProductStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "status 값이 올바르지 않습니다.");
        }
    }
}
