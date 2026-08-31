package kr.savepick.store.api;

import kr.savepick.store.application.StoreQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-009 매장 정보 조회 (11-api-spec.md §1, FR-033). 비로그인 포함 전체 공개 — SecurityConfig에서
 * permitAll로 열어둔다 (BR-001, BR-014).
 */
@RestController
@RequestMapping("/api/store")
public class StoreController {

    private final StoreQueryService storeQueryService;

    public StoreController(StoreQueryService storeQueryService) {
        this.storeQueryService = storeQueryService;
    }

    @GetMapping
    public StoreResponse getStore() {
        return StoreResponse.from(storeQueryService.getStore());
    }
}
