package kr.savepick.stock.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.savepick.stock.domain.ProductStock;
import kr.savepick.stock.domain.ProductStockRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 13번 §3, §7.1 — {@code findForUpdate}가 이 재고 동시성 설계의 1층 방어선이다.
 */
public interface ProductStockJpaRepository extends JpaRepository<ProductStock, Long>, ProductStockRepository {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ProductStock s where s.productId = :productId")
    Optional<ProductStock> findByProductIdForUpdate(@Param("productId") Long productId);

    @Override
    @Query("select s from ProductStock s where s.productId = :productId")
    Optional<ProductStock> findByProductId(@Param("productId") Long productId);

    /**
     * 델타를 기존 값에 더한다 (행이 있을 때만 영향을 준다). PostgreSQL은 {@code INSERT ... ON CONFLICT
     * DO UPDATE}에서 실제 충돌 여부와 무관하게 제안된(INSERT) 행 자체를 CHECK 제약으로 먼저 검증한다.
     * 델타 값(예: HOLD의 delta_total=0, delta_held=+3)은 그 자체로는 유효한 절대값이 아니므로
     * ({@code available = 0 - 3 - 0 < 0}), 실제로 충돌해 DO UPDATE로 귀결되더라도 INSERT 단계에서
     * 먼저 제약 위반으로 거부된다 — 그래서 UPSERT를 한 문장으로 쓰지 않고 UPDATE와 INSERT로 나눈다
     * (아래 {@link #applyDelta} 참고).
     */
    @Modifying(clearAutomatically = true)
    @Query("update ProductStock p set "
            + "p.totalQuantity = p.totalQuantity + :deltaTotal, "
            + "p.heldQuantity = p.heldQuantity + :deltaHeld, "
            + "p.confirmedQuantity = p.confirmedQuantity + :deltaConfirmed, "
            + "p.discardedQuantity = p.discardedQuantity + :deltaDiscarded, "
            + "p.updatedAt = :now "
            + "where p.productId = :productId")
    int updateDelta(
            @Param("productId") Long productId,
            @Param("deltaTotal") int deltaTotal,
            @Param("deltaHeld") int deltaHeld,
            @Param("deltaConfirmed") int deltaConfirmed,
            @Param("deltaDiscarded") int deltaDiscarded,
            @Param("now") LocalDateTime now);

    /** 재고 최초 등록 — 델타 값이 곧 초기 절대값이다 (이전 값이 전부 0이므로). */
    @Modifying(clearAutomatically = true)
    @Query(value = "INSERT INTO product_stocks "
            + "(product_id, total_quantity, held_quantity, confirmed_quantity, discarded_quantity, updated_at) "
            + "VALUES (:productId, :deltaTotal, :deltaHeld, :deltaConfirmed, :deltaDiscarded, :now)",
            nativeQuery = true)
    int insertInitial(
            @Param("productId") Long productId,
            @Param("deltaTotal") int deltaTotal,
            @Param("deltaHeld") int deltaHeld,
            @Param("deltaConfirmed") int deltaConfirmed,
            @Param("deltaDiscarded") int deltaDiscarded,
            @Param("now") LocalDateTime now);

    /**
     * 재고 등록·조정의 유일한 쓰기 경로 (14-project-structure.md §5 강제 규칙 1, §9.2 —
     * 더티 체킹이 아닌 명시적 쿼리). 먼저 UPDATE를 시도하고, 영향받은 행이 없으면(최초 등록)
     * INSERT한다. 호출자가 이미 {@code findByProductIdForUpdate}로 행을 잠갔다면 이 메서드는
     * 항상 UPDATE 경로를 taken한다 — INSERT 경로는 오직 처음 등록할 때만 실행된다.
     */
    @Override
    default int applyDelta(Long productId, int deltaTotal, int deltaHeld, int deltaConfirmed, int deltaDiscarded, LocalDateTime now) {
        int updated = updateDelta(productId, deltaTotal, deltaHeld, deltaConfirmed, deltaDiscarded, now);
        if (updated == 0) {
            return insertInitial(productId, deltaTotal, deltaHeld, deltaConfirmed, deltaDiscarded, now);
        }
        return updated;
    }

    /** BATCH-04 대상 조회 — 재고 행이 있는 모든 상품 (13-inventory-concurrency.md §9.1). */
    @Query("select s.productId from ProductStock s order by s.productId asc")
    List<Long> findAllProductIds();
}
