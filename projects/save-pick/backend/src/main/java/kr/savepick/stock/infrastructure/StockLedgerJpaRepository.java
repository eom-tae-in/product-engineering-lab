package kr.savepick.stock.infrastructure;

import kr.savepick.stock.domain.StockLedger;
import kr.savepick.stock.domain.StockLedgerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockLedgerJpaRepository extends JpaRepository<StockLedger, Long>, StockLedgerRepository {

    Page<StockLedger> findByProductIdOrderByOccurredAtDesc(Long productId, Pageable pageable);

    /**
     * BATCH-04 — {@code product_stocks.total_quantity}의 기대값
     * (13-inventory-concurrency.md §9.1). 총 재고를 바꾸는 모든 경로가 원장을 남기므로
     * (14-project-structure.md §5 강제 규칙 1) 누적 합이 현재 값과 같아야 한다.
     */
    @Query("select coalesce(sum(l.deltaTotal), 0) from StockLedger l where l.productId = :productId")
    int sumDeltaTotalByProductId(@Param("productId") Long productId);
}
