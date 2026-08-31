package kr.savepick.store.api;

import jakarta.validation.Valid;
import java.time.ZoneId;
import kr.savepick.store.application.StoreQueryService;
import kr.savepick.store.application.StoreSettingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-120·API-121 매장 운영 설정 조회·변경 (11-api-spec.md §10). 관리자 전용 —
 * SecurityConfig가 /api/admin/**을 ROLE_ADMIN으로 이미 막지만, account 슬라이스의 MeController처럼
 * 컨트롤러에도 명시적으로 표시해둔다.
 */
@RestController
@RequestMapping("/api/admin/store-settings")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminStoreSettingController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final StoreQueryService storeQueryService;
    private final StoreSettingService storeSettingService;

    public AdminStoreSettingController(StoreQueryService storeQueryService, StoreSettingService storeSettingService) {
        this.storeQueryService = storeQueryService;
        this.storeSettingService = storeSettingService;
    }

    /** API-120. */
    @GetMapping
    public StoreSettingsResponse getSettings() {
        return StoreSettingsResponse.from(storeQueryService.getStoreSettings());
    }

    /** API-121. BR-003, BR-014, BR-016. */
    @PutMapping
    public UpdateStoreSettingsResponse updateSettings(@Valid @RequestBody UpdateStoreSettingsRequest request) {
        StoreSettingService.UpdateResult result = storeSettingService.updateSettings(
                request.openTime(),
                request.closeTime(),
                request.defaultSlotCapacity().shortValue(),
                request.holidays());
        return new UpdateStoreSettingsResponse(
                result.store().getOpenTime(),
                result.store().getCloseTime(),
                result.store().getDefaultSlotCapacity(),
                result.holidays(),
                result.excludedFutureSlotCount(),
                result.keptConfirmedOrderCount(),
                result.appliedFrom().atZone(ZONE).toOffsetDateTime());
    }
}
