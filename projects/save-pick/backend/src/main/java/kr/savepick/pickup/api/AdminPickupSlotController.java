package kr.savepick.pickup.api;

import jakarta.validation.Valid;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.pickup.application.PickupSlotAdminService;
import kr.savepick.pickup.application.PickupSlotQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-118·119 관리자 픽업 운영 (11-api-spec.md §10). 관리자 전용 — SecurityConfig가
 * {@code /api/admin/**}을 ROLE_ADMIN으로 막지만 컨트롤러에도 명시한다.
 */
@RestController
@RequestMapping("/api/admin/pickup-slots")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminPickupSlotController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final PickupSlotQueryService pickupSlotQueryService;
    private final PickupSlotAdminService pickupSlotAdminService;
    private final ServerClock serverClock;

    public AdminPickupSlotController(
            PickupSlotQueryService pickupSlotQueryService, PickupSlotAdminService pickupSlotAdminService, ServerClock serverClock) {
        this.pickupSlotQueryService = pickupSlotQueryService;
        this.pickupSlotAdminService = pickupSlotAdminService;
        this.serverClock = serverClock;
    }

    /** API-118. FR-055, FR-057, FR-058. */
    @GetMapping
    public AdminPickupSlotOverviewResponse getOverview(@RequestParam String date) {
        LocalDate parsedDate = parseDate(date);
        PickupSlotQueryService.AdminSlotOverview overview = pickupSlotQueryService.getAdminOverview(parsedDate, serverClock.now());
        return AdminPickupSlotOverviewResponse.from(overview, ZONE);
    }

    /** API-119. FR-057, FR-058. */
    @PatchMapping("/{slotId}")
    public UpdatePickupSlotResponse update(@PathVariable Long slotId, @Valid @RequestBody UpdatePickupSlotRequest request) {
        PickupSlotAdminService.UpdateResult result = pickupSlotAdminService.update(slotId, request.capacity(), request.blocked());
        return UpdatePickupSlotResponse.from(result);
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "date 형식이 올바르지 않습니다(YYYY-MM-DD).");
        }
    }
}
