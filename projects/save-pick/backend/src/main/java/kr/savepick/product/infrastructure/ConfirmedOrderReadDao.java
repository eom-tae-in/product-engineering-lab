package kr.savepick.product.infrastructure;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

/**
 * 상품 변경이 이미 확정된 주문에 미치는 영향을 세는 읽기 전용 DAO (API-105·API-106).
 *
 * <p>14-project-structure.md §4.1은 {@code product ──► order} 의존을 두지 않는다. 그래서
 * order 도메인의 자바 타입을 import하지 않는 네이티브 쿼리로 참조한다 — stock의
 * {@code ConfirmedSaleQuantityReadDao}·{@code OrderNoReadDao}, account의
 * {@code OrderNoShowReadDao}와 같은 패턴이다. {@code orders}에 @Entity를 새로 매핑하지
 * 않는다(order 도메인의 진짜 엔티티와 같은 테이블을 두 번 매핑하지 않기 위해서다).
 *
 * <p>"확정 주문"은 아직 이행이 남은 {@code CONFIRMED}·{@code READY}만 센다. 05 §5.2가
 * 판매 상태 전환·마감 단축에서 "취소하지 않고 유지한다"고 말하는 대상이 이 둘이다.
 * 이미 끝난 {@code COMPLETED}와 종결된 {@code CANCELED}·{@code NO_SHOW}는 이 변경으로
 * 영향받을 여지가 없다.
 */
@Repository
public class ConfirmedOrderReadDao {

    private static final String ACTIVE_CONFIRMED_STATUSES = "'CONFIRMED', 'READY'";

    private final EntityManager entityManager;

    public ConfirmedOrderReadDao(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** 이 상품을 담은 확정 주문 수 (API-106 {@code keptConfirmedOrderCount}). */
    public int countConfirmedOrders(Long productId) {
        Object result = entityManager.createNativeQuery(
                        "SELECT COUNT(DISTINCT o.id) FROM orders o "
                                + "JOIN order_items oi ON oi.order_id = o.id "
                                + "WHERE oi.product_id = :productId "
                                + "AND o.status IN (" + ACTIVE_CONFIRMED_STATUSES + ")")
                .setParameter("productId", productId)
                .getSingleResult();
        return ((Number) result).intValue();
    }

    /**
     * 마감 시각을 {@code newClosingAt}으로 앞당겼을 때 픽업 시간대가 그보다 늦어 영향을 받는
     * 확정 주문 수 (API-105 {@code affectedConfirmedOrderCount}).
     *
     * <p>픽업 시각은 {@code orders}가 아니라 지정된 {@code pickup_slots}에 있어 조인이 필요하다.
     * 시간대를 아직 고르지 않은 주문은 확정 상태가 될 수 없으므로 조인에서 자연히 빠진다.
     */
    public int countConfirmedOrdersPickedUpAfter(Long productId, LocalDateTime newClosingAt) {
        Object result = entityManager.createNativeQuery(
                        "SELECT COUNT(DISTINCT o.id) FROM orders o "
                                + "JOIN order_items oi ON oi.order_id = o.id "
                                + "JOIN pickup_slots s ON s.id = o.pickup_slot_id "
                                + "WHERE oi.product_id = :productId "
                                + "AND o.status IN (" + ACTIVE_CONFIRMED_STATUSES + ") "
                                + "AND s.start_at > :newClosingAt")
                .setParameter("productId", productId)
                .setParameter("newClosingAt", newClosingAt)
                .getSingleResult();
        return ((Number) result).intValue();
    }
}
