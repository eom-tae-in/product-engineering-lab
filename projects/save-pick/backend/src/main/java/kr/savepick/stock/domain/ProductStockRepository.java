package kr.savepick.stock.domain;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ProductStockRepository {

    /** 13번 §3 — {@code SELECT ... FOR UPDATE}. 행이 없으면(재고 미등록) 비어 있다. */
    Optional<ProductStock> findByProductIdForUpdate(Long productId);

    Optional<ProductStock> findByProductId(Long productId);

    /**
     * 재고 등록·조정의 유일한 쓰기 경로 (14-project-structure.md §5 강제 규칙 1).
     * 행이 없으면 델타 값을 초깃값으로 삼아 새로 만들고(재고 최초 등록), 있으면 기존 값에 더한다
     * ({@code INSERT ... ON CONFLICT (product_id) DO UPDATE}). 항상 1행에 영향을 준다.
     * 이 메서드는 {@code stock/application/StockLedgerRecorder}를 통해서만 호출한다.
     */
    int applyDelta(Long productId, int deltaTotal, int deltaHeld, int deltaConfirmed, int deltaDiscarded, LocalDateTime now);
}
