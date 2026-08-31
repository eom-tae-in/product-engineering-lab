package kr.savepick.stock.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * order 도메인이 아직 없어 재고 이력(API-111)의 {@code orderNo} 표시를 위해 orders 테이블을
 * 읽기 전용 네이티브 쿼리로 참조한다. account 슬라이스의 {@code OrderNoShowReadDao}와 같은
 * 임시 예외 패턴이다 — order 도메인이 생기면 그 도메인의 조회 서비스로 대체돼야 한다.
 * orders 테이블에 @Entity를 새로 매핑하지 않는다.
 */
@Repository
public class OrderNoReadDao {

    private final EntityManager entityManager;

    public OrderNoReadDao(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @SuppressWarnings("unchecked")
    public Map<Long, String> findOrderNos(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        List<Tuple> rows = entityManager.createNativeQuery(
                        "SELECT id, order_no FROM orders WHERE id IN :orderIds", Tuple.class)
                .setParameter("orderIds", orderIds)
                .getResultList();
        Map<Long, String> result = new HashMap<>();
        for (Tuple row : rows) {
            result.put(((Number) row.get(0)).longValue(), (String) row.get(1));
        }
        return result;
    }
}
