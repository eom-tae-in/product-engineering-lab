package kr.savepick.store.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

/**
 * API-121 매장 운영 설정 변경의 영향 범위 계산 (FR-056). {@code pickup_slots}를 읽기 전용
 * 네이티브 쿼리로 참조한다 — account의 {@code OrderNoShowReadDao}, stock의 {@code OrderNoReadDao}와
 * 같은 패턴이다.
 *
 * <p>14-project-structure.md §4.1은 {@code pickup ─► store}(읽기) 방향만 명시하고 그 반대는
 * 두지 않는다. 이 기능(영업 종료 시각 단축이 미래 슬롯·기존 확정 주문에 미치는 영향 계산)은
 * 구조상 store가 pickup의 데이터를 읽어야만 답할 수 있다. {@code pickup.*} 자바 타입을
 * import하는 대신 네이티브 쿼리로 우회해 도메인 간 코드 의존(순환 위험)은 만들지 않는다 —
 * 스키마 결합은 남지만, 이는 이미 이 저장소가 두 곳(account, stock)에서 받아들인 절충이다
 * (2단계 구현 보고 참고).
 *
 * <p>{@code reserved_count}는 CONFIRMED·READY·COMPLETED 주문 수와 같다(취소·노쇼에서만
 * 줄어든다, 13번 §8). 그래서 {@code orders} 테이블을 따로 읽지 않고 이 컬럼만으로 두 지표를
 * 모두 구할 수 있다.
 */
@Repository
public class StoreSettingImpactReadDao {

    private final EntityManager entityManager;

    public StoreSettingImpactReadDao(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * @param now              판정 기준 시각 — 이미 시작한 슬롯은 영향 계산에서 제외한다
     * @param today            BATCH-05가 만드는 D+0 날짜
     * @param todayCutoff      {@code today}일의 새 영업 종료 시각(예: 오늘 21:00)
     * @param tomorrow         D+1 날짜
     * @param tomorrowCutoff   {@code tomorrow}일의 새 영업 종료 시각
     */
    public Impact measure(
            LocalDateTime now, LocalDate today, LocalDateTime todayCutoff, LocalDate tomorrow, LocalDateTime tomorrowCutoff) {
        Tuple row = (Tuple) entityManager.createNativeQuery(
                        "SELECT "
                                + "  COALESCE(SUM(CASE WHEN reserved_count = 0 THEN 1 ELSE 0 END), 0) AS excluded_slots, "
                                + "  COALESCE(SUM(CASE WHEN reserved_count > 0 THEN reserved_count ELSE 0 END), 0) AS kept_orders "
                                + "FROM pickup_slots "
                                + "WHERE start_at > :now "
                                + "  AND ((slot_date = :today AND start_at >= :todayCutoff) "
                                + "    OR (slot_date = :tomorrow AND start_at >= :tomorrowCutoff))",
                        Tuple.class)
                .setParameter("now", now)
                .setParameter("today", today)
                .setParameter("tomorrow", tomorrow)
                .setParameter("todayCutoff", todayCutoff)
                .setParameter("tomorrowCutoff", tomorrowCutoff)
                .getSingleResult();
        return new Impact(((Number) row.get("excluded_slots")).intValue(), ((Number) row.get("kept_orders")).intValue());
    }

    public record Impact(int excludedFutureSlotCount, int keptConfirmedOrderCount) {
    }
}
