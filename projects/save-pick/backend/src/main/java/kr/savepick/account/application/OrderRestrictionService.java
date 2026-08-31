package kr.savepick.account.application;

import java.time.LocalDateTime;
import java.util.List;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.account.domain.MemberRestriction;
import kr.savepick.account.domain.MemberRestrictionRepository;
import kr.savepick.account.domain.OrderPermission;
import kr.savepick.account.infrastructure.OrderNoShowReadDao;
import kr.savepick.account.infrastructure.OrderNoShowReadDao.NoShowOrderRow;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-007 노쇼·주문 제한 상태 조회 (BR-023, FR-031, FR-032) + BATCH-03이 호출하는 제재 생성.
 *
 * <p>{@code noShowOrders}는 orders 테이블을 읽어야 하는데, order 도메인이 생긴 지금도
 * {@link OrderNoShowReadDao}의 읽기 전용 네이티브 쿼리를 그대로 유지한다 — 14-project-structure.md
 * §4.1의 "account ─ (의존 없음)" 원칙과, order 도메인의 애플리케이션 서비스를 호출하는 방식 중
 * 하나를 고른 것이다. 네이티브 쿼리는 {@code order.*} 자바 타입을 import하지 않으므로 코드
 * 의존은 생기지 않는다(테이블 스키마 의존은 남지만, 그건 두 방식 모두 피할 수 없다). 반대로
 * {@code order/application}의 조회 서비스를 호출하면 §4.1이 명시한 의존 방향(account는 어떤
 * 도메인도 의존하지 않는다)을 정면으로 어기고, order가 배치를 통해 account를 호출하는 것과
 * 합쳐 순환 참조를 만든다. 그래서 이번 슬라이스에서도 네이티브 쿼리 방식을 유지하기로 판단했다
 * (2단계 구현 보고 참고).
 *
 * <p>{@code orderPermission}은 {@code members.order_permission}(저장된 값)이 아니라 {@code
 * member_restrictions.ends_at}을 조회 시점에 다시 판정해 계산한다 — 저장된 컬럼은 BATCH-03이
 * 제재를 걸 때만 갱신되고 7일 경과를 되돌리는 배치가 없어(아래 {@link #applyNoShowRestriction}
 * 주석, 11번 BATCH-03) 그대로 두면 만료된 뒤에도 영구히 RESTRICTED로 남는다.
 */
@Service
public class OrderRestrictionService {

    private final MemberRepository memberRepository;
    private final MemberRestrictionRepository memberRestrictionRepository;
    private final OrderNoShowReadDao orderNoShowReadDao;
    private final ServerClock serverClock;
    private final int windowDays;
    private final int noShowThreshold;
    private final int restrictionDays;

    public OrderRestrictionService(
            MemberRepository memberRepository,
            MemberRestrictionRepository memberRestrictionRepository,
            OrderNoShowReadDao orderNoShowReadDao,
            ServerClock serverClock,
            @Value("${savepick.restriction.no-show-window-days}") int windowDays,
            @Value("${savepick.restriction.no-show-threshold}") int noShowThreshold,
            @Value("${savepick.restriction.restriction-days}") int restrictionDays) {
        this.memberRepository = memberRepository;
        this.memberRestrictionRepository = memberRestrictionRepository;
        this.orderNoShowReadDao = orderNoShowReadDao;
        this.serverClock = serverClock;
        this.windowDays = windowDays;
        this.noShowThreshold = noShowThreshold;
        this.restrictionDays = restrictionDays;
    }

    /** API-017 주문서 생성 차단 판정도 이 메서드의 {@code orderPermission()}을 기준으로 삼는다. */
    @Transactional(readOnly = true)
    public NoShowStatus getStatus(Long memberId) {
        memberRepository.findById(memberId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));

        LocalDateTime now = serverClock.now();
        LocalDateTime since = now.minusDays(windowDays);
        List<NoShowOrderRow> noShows = orderNoShowReadDao.findRecentNoShows(memberId, since);

        LocalDateTime restrictedUntil = memberRestrictionRepository.findTopByMemberIdOrderByEndsAtDesc(memberId)
                .map(MemberRestriction::getEndsAt)
                .filter(endsAt -> endsAt.isAfter(now))
                .orElse(null);
        OrderPermission effectivePermission = restrictedUntil != null ? OrderPermission.RESTRICTED : OrderPermission.ALLOWED;

        return new NoShowStatus(noShows.size(), windowDays, effectivePermission, restrictedUntil, noShows);
    }

    /**
     * BATCH-03이 주문 1건을 NO_SHOW로 전환한 직후 호출한다(order/batch/NoShowDetectionJob →
     * order/application/NoShowService → 이 메서드, 14-project-structure.md §6.1). 최근
     * {@code windowDays}일 내 NO_SHOW 건수가 정확히 {@code noShowThreshold}(3)에 도달했고
     * 활성 제한(ends_at &gt; now)이 없는 시점에만 {@code member_restrictions} 1행을 만든다
     * (11번 BATCH-03) — 4·5번째 노쇼에서는 만들지 않는다.
     *
     * <p>7일 뒤 원상 복귀는 별도 배치가 아니라 {@link #getStatus} 같은 조회 시점 판정으로
     * 처리한다(11번 BATCH-03 — "제한 해제는 별도 배치를 두지 않는다"). {@code members
     * .order_permission}은 여기서 RESTRICTED로 갱신해 두지만 이후 되돌리는 배치는 두지 않는다 —
     * 그 컬럼을 직접 읽는 API-005(내 정보 조회)의 표시값은 만료 후에도 한동안 RESTRICTED로
     * 보일 수 있다(알려진 한계, 2단계 구현 보고 참고). 주문 가능 여부를 실제로 판정하는 모든
     * 경로(API-017, API-007)는 이 컬럼이 아니라 {@link #getStatus}를 거친다.
     */
    @Transactional
    public boolean applyNoShowRestriction(Long memberId, Long triggerOrderId, LocalDateTime now) {
        LocalDateTime since = now.minusDays(windowDays);
        List<NoShowOrderRow> noShows = orderNoShowReadDao.findRecentNoShows(memberId, since);
        if (noShows.size() != noShowThreshold) {
            return false;
        }
        boolean hasActiveRestriction = memberRestrictionRepository.findTopByMemberIdOrderByEndsAtDesc(memberId)
                .map(MemberRestriction::getEndsAt)
                .filter(endsAt -> endsAt.isAfter(now))
                .isPresent();
        if (hasActiveRestriction) {
            return false;
        }

        MemberRestriction restriction = MemberRestriction.create(
                memberId, triggerOrderId, (short) noShowThreshold, now, now.plusDays(restrictionDays));
        try {
            memberRestrictionRepository.save(restriction);
        } catch (DataIntegrityViolationException e) {
            // UQ_member_restrictions_member_trigger — 같은 주문으로 이미 제재가 생성됐다(배치 재실행 등).
            return false;
        }

        memberRepository.findById(memberId).ifPresent(member -> {
            member.restrict(now);
            memberRepository.save(member);
        });
        return true;
    }

    public record NoShowStatus(
            int recentNoShowCount,
            int windowDays,
            OrderPermission orderPermission,
            LocalDateTime restrictedUntil,
            List<NoShowOrderRow> noShowOrders) {
    }
}
