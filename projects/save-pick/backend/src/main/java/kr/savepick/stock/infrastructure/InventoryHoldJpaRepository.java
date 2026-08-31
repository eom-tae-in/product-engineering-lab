package kr.savepick.stock.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import kr.savepick.stock.domain.HoldStatus;
import kr.savepick.stock.domain.InventoryHold;
import kr.savepick.stock.domain.InventoryHoldRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryHoldJpaRepository extends JpaRepository<InventoryHold, Long>, InventoryHoldRepository {

    @Override
    List<InventoryHold> findByOrderId(Long orderId);

    @Override
    List<InventoryHold> findByOrderIdAndStatus(Long orderId, HoldStatus status);

    @Override
    List<InventoryHold> findByProductIdAndStatusAndExpiresAtLessThanEqual(
            Long productId, HoldStatus status, LocalDateTime now);

    /** 14-project-structure.md §5 강제 규칙 2 — 항상 조건부(WHERE status = :expected) UPDATE로 실행한다. */
    @Override
    @Modifying
    @Query("update InventoryHold h set h.status = :next, h.settledAt = :now "
            + "where h.id = :id and h.status = :expected")
    int transitionStatus(
            @Param("id") Long id,
            @Param("expected") HoldStatus expected,
            @Param("next") HoldStatus next,
            @Param("now") LocalDateTime now);

    /** 13번 §2.1 읽기 보정 — 상품별 만료됐지만 아직 회수되지 않은 선점 합계. */
    @Query("select h.productId as productId, coalesce(sum(h.quantity), 0) as quantity "
            + "from InventoryHold h "
            + "where h.status = kr.savepick.stock.domain.HoldStatus.HELD "
            + "and h.expiresAt <= :now and h.productId in :productIds "
            + "group by h.productId")
    List<ExpiredHeldRow> sumExpiredHeldByProductIds(@Param("productIds") List<Long> productIds, @Param("now") LocalDateTime now);

    /** API-110 관리자 재고 현황 — 만료된 선점을 제외한 유효 선점 합계 (FR-046, 05 C5). */
    @Query("select coalesce(sum(h.quantity), 0) from InventoryHold h "
            + "where h.productId = :productId and h.status = kr.savepick.stock.domain.HoldStatus.HELD "
            + "and h.expiresAt > :now")
    int sumActiveHeldByProductId(@Param("productId") Long productId, @Param("now") LocalDateTime now);

    interface ExpiredHeldRow {
        Long getProductId();

        Long getQuantity();
    }
}
