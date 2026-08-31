package kr.savepick.account.api;

import java.time.ZoneId;
import java.util.List;
import jakarta.validation.Valid;
import kr.savepick.account.application.MemberProfileService;
import kr.savepick.account.application.OrderRestrictionService;
import kr.savepick.account.domain.Member;
import kr.savepick.account.infrastructure.AuthenticatedPrincipal;
import kr.savepick.account.infrastructure.OrderNoShowReadDao.NoShowOrderRow;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-005~007 (11-api-spec.md §1). 경로에 회원 ID를 두지 않는다 — /api/me로만 접근한다 (12번 §3.3).
 */
@RestController
@RequestMapping("/api/me")
@PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
public class MeController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final MemberProfileService memberProfileService;
    private final OrderRestrictionService orderRestrictionService;

    public MeController(MemberProfileService memberProfileService, OrderRestrictionService orderRestrictionService) {
        this.memberProfileService = memberProfileService;
        this.orderRestrictionService = orderRestrictionService;
    }

    /** API-005. */
    @GetMapping
    public MeResponse getMe(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        Member member = memberProfileService.getProfile(principal.memberId());
        return new MeResponse(member.getId(), member.getEmail(), member.getName(), member.getPhone(), member.getOrderPermission());
    }

    /** API-006. */
    @PatchMapping
    public UpdateMeResponse updateMe(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                      @Valid @RequestBody UpdateMeRequest request) {
        Member member = memberProfileService.updateProfile(principal.memberId(), request.name(), request.phone());
        return new UpdateMeResponse(member.getId(), member.getEmail(), member.getName(), member.getPhone());
    }

    /** API-007. */
    @GetMapping("/no-show-status")
    public NoShowStatusResponse getNoShowStatus(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        OrderRestrictionService.NoShowStatus status = orderRestrictionService.getStatus(principal.memberId());
        List<NoShowStatusResponse.NoShowOrderItem> items = status.noShowOrders().stream()
                .map(this::toItem)
                .toList();
        return new NoShowStatusResponse(
                status.recentNoShowCount(),
                status.windowDays(),
                status.orderPermission(),
                status.restrictedUntil() == null ? null : status.restrictedUntil().atZone(ZONE).toOffsetDateTime(),
                items);
    }

    private NoShowStatusResponse.NoShowOrderItem toItem(NoShowOrderRow row) {
        return new NoShowStatusResponse.NoShowOrderItem(row.orderNo(), row.noShowAt().atZone(ZONE).toOffsetDateTime());
    }
}
