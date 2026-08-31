package kr.savepick.account.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * orders 테이블을 읽기 전용 네이티브 쿼리로 참조한다.
 *
 * <p>order 도메인이 이제 존재하지만(2단계), 이 DAO는 계속 유지하기로 판단했다 —
 * 14-project-structure.md §4.1은 "account ─ (의존 없음)"을 명시한다. order 도메인의
 * 애플리케이션 서비스를 호출하는 방식으로 바꾸면 account가 order에 코드 의존을 갖게 되고,
 * order의 배치(BATCH-03)가 제재 생성을 위해 다시 account를 호출하는 것과 합쳐 순환이 생긴다.
 * 네이티브 쿼리는 {@code order.*} 자바 타입을 import하지 않으므로 그 의존이 생기지 않는다
 * (테이블 스키마 결합은 남지만, 이는 서비스 호출 방식으로 바꿔도 피할 수 없다). 읽기 전용
 * 조회이고 order 쪽 트랜잭션 경계에 낄 필요가 없다는 실용성도 함께 고려했다(2단계 구현 보고
 * 참고). orders 테이블에 @Entity를 새로 매핑하지 않는다 — order 도메인의 진짜 {@code Order}
 * 엔티티와 같은 테이블을 매핑해 영속성 컨텍스트 충돌이 생기지 않게 하기 위함이다.
 */
@Repository
public class OrderNoShowReadDao {

    private final EntityManager entityManager;
    private final ZoneId zone;

    public OrderNoShowReadDao(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.zone = ZoneId.of("Asia/Seoul");
    }

    @SuppressWarnings("unchecked")
    public List<NoShowOrderRow> findRecentNoShows(Long memberId, LocalDateTime since) {
        List<Tuple> rows = entityManager.createNativeQuery(
                        "SELECT order_no, no_show_at FROM orders "
                                + "WHERE member_id = :memberId AND status = 'NO_SHOW' AND no_show_at >= :since "
                                + "ORDER BY no_show_at DESC",
                        Tuple.class)
                .setParameter("memberId", memberId)
                .setParameter("since", since)
                .getResultList();

        return rows.stream()
                .map(row -> new NoShowOrderRow((String) row.get(0), toLocalDateTime(row.get(1))))
                .toList();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof OffsetDateTime odt) {
            return odt.atZoneSameInstant(zone).toLocalDateTime();
        }
        if (value instanceof java.time.Instant instant) {
            return java.time.LocalDateTime.ofInstant(instant, zone);
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        throw new IllegalStateException("예상하지 못한 시각 타입: " + value.getClass());
    }

    public record NoShowOrderRow(String orderNo, LocalDateTime noShowAt) {
    }
}
