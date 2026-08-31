package kr.savepick.stock.api;

import jakarta.validation.Valid;
import java.time.ZoneId;
import kr.savepick.account.infrastructure.AuthenticatedPrincipal;
import kr.savepick.common.response.PageMeta;
import kr.savepick.common.time.ServerClock;
import kr.savepick.stock.application.StockAdjustService;
import kr.savepick.stock.application.StockQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-109~111 관리자 재고 등록·조정·현황·이력 (11-api-spec.md §8). 관리자 전용 —
 * SecurityConfig가 /api/admin/**을 ROLE_ADMIN으로 막지만 컨트롤러에도 명시한다.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminStockController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final StockAdjustService stockAdjustService;
    private final StockQueryService stockQueryService;
    private final ServerClock serverClock;

    public AdminStockController(StockAdjustService stockAdjustService, StockQueryService stockQueryService, ServerClock serverClock) {
        this.stockAdjustService = stockAdjustService;
        this.stockQueryService = stockQueryService;
        this.serverClock = serverClock;
    }

    /** API-109. BR-006, BR-025. */
    @PutMapping("/products/{productId}/stock")
    public AdjustStockResponse adjustStock(
            @PathVariable Long productId,
            @Valid @RequestBody AdjustStockRequest request,
            @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        StockAdjustService.AdjustResult result =
                stockAdjustService.adjust(productId, request.totalQuantity(), request.note(), principal.memberId());
        return AdjustStockResponse.of(productId, result, ZONE);
    }

    /** API-110. FR-046. */
    @GetMapping("/stocks")
    public StockOverviewListResponse getOverview(
            @RequestParam(defaultValue = "false") boolean onlyUnavailable,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        StockQueryService.OverviewPage result = stockQueryService.getOverview(onlyUnavailable, page, size);
        return new StockOverviewListResponse(
                serverClock.now().atZone(ZONE).toOffsetDateTime(),
                result.items().stream().map(StockOverviewItemResponse::from).toList(),
                new PageMeta(result.number(), result.size(), result.totalElements()));
    }

    /** API-111. FR-047. */
    @GetMapping("/stocks/{productId}/ledger")
    public StockLedgerListResponse getLedger(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        StockQueryService.LedgerPage result = stockQueryService.getLedger(productId, page, size);
        return new StockLedgerListResponse(
                result.items().stream().map(item -> StockLedgerItemResponse.from(item, ZONE)).toList(),
                new PageMeta(result.number(), result.size(), result.totalElements()));
    }
}
