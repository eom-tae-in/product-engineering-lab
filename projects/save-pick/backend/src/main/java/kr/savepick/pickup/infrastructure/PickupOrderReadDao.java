package kr.savepick.pickup.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * API-118 시간대별 픽업 현황의 {@code itemTotals} 집계 전용 읽기 전용 DAO.
 *
 * <p>14-project-structure.md §4.1의 의존 방향표는 {@code order ──► pickup}만 허용하고
 * {@code pickup ──► order}는 두지 않는다(순환 방지). 그런데 시간대별 품목 합계는 {@code orders}·
 * {@code order_items}의 데이터가 필요하다. 이 DAO는 두 테이블을 네이티브 SQL로만 읽어(=order
 * 도메인의 엔티티·리포지토리·애플리케이션 서비스를 자바 코드로 참조하지 않는다) 코드 의존 순환을
 * 만들지 않으면서 필요한 집계를 제공한다. {@code order_items.product_name}이 주문 시점 스냅샷이라
 * {@code product} 도메인도 참조하지 않는다.
 */
@Repository
public class PickupOrderReadDao {

    private final EntityManager entityManager;

    public PickupOrderReadDao(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** BR-016 — 정원 점유 대상과 같은 상태(CONFIRMED·READY·COMPLETED)의 주문 품목만 합산한다. */
    @SuppressWarnings("unchecked")
    public List<ItemTotalRow> sumItemsBySlotId(Long slotId) {
        List<Tuple> rows = entityManager.createNativeQuery(
                        "SELECT oi.product_id, oi.product_name, SUM(oi.quantity) "
                                + "FROM order_items oi JOIN orders o ON oi.order_id = o.id "
                                + "WHERE o.pickup_slot_id = :slotId AND o.status IN ('CONFIRMED', 'READY', 'COMPLETED') "
                                + "GROUP BY oi.product_id, oi.product_name "
                                + "ORDER BY oi.product_id",
                        Tuple.class)
                .setParameter("slotId", slotId)
                .getResultList();

        List<ItemTotalRow> result = new ArrayList<>();
        for (Tuple row : rows) {
            result.add(new ItemTotalRow(
                    ((Number) row.get(0)).longValue(), (String) row.get(1), ((Number) row.get(2)).intValue()));
        }
        return result;
    }

    public record ItemTotalRow(Long productId, String productName, int quantity) {
    }
}
