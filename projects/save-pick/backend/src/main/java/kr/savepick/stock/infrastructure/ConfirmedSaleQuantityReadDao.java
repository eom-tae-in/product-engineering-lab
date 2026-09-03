package kr.savepick.stock.infrastructure;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

/**
 * BATCH-04 재고 정합성 점검(13-inventory-concurrency.md §9.1)의 기대값 한 축 —
 * 상품별 "확정 판매 주문(CONFIRMED·READY·COMPLETED·NO_SHOW)의 품목 수량 합"을 읽는다.
 *
 * <p>14-project-structure.md §4.1은 {@code stock ──► order} 의존을 허용하지 않는다.
 * 그래서 order 도메인의 자바 타입을 import하지 않는 읽기 전용 네이티브 쿼리로 참조한다 —
 * 같은 패키지의 {@code OrderNoReadDao}, account의 {@code OrderNoShowReadDao}와 같은 패턴이다.
 * {@code orders}·{@code order_items}에 @Entity를 새로 매핑하지 않는다(order 도메인의 진짜
 * 엔티티와 같은 테이블을 두 번 매핑해 영속성 컨텍스트가 충돌하는 것을 막는다).
 *
 * <p>취소(CANCELED)는 {@code CANCEL_RESTORE}·{@code CANCEL_DISCARD} 원장으로
 * {@code confirmed_quantity}를 되돌리므로 합계에서 빠지고, 노쇼(NO_SHOW)는 재고를 되돌리지
 * 않으므로(BR-022, 05 S10) 합계에 남는다.
 */
@Repository
public class ConfirmedSaleQuantityReadDao {

    private static final String CONFIRMED_SALE_STATUSES = "'CONFIRMED', 'READY', 'COMPLETED', 'NO_SHOW'";

    private final EntityManager entityManager;

    public ConfirmedSaleQuantityReadDao(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public int sumConfirmedSaleQuantity(Long productId) {
        Object result = entityManager.createNativeQuery(
                        "SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi "
                                + "JOIN orders o ON o.id = oi.order_id "
                                + "WHERE oi.product_id = :productId "
                                + "AND o.status IN (" + CONFIRMED_SALE_STATUSES + ")")
                .setParameter("productId", productId)
                .getSingleResult();
        return ((Number) result).intValue();
    }
}
