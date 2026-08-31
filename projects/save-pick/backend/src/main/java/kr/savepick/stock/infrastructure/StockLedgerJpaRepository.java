package kr.savepick.stock.infrastructure;

import kr.savepick.stock.domain.StockLedger;
import kr.savepick.stock.domain.StockLedgerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockLedgerJpaRepository extends JpaRepository<StockLedger, Long>, StockLedgerRepository {

    Page<StockLedger> findByProductIdOrderByOccurredAtDesc(Long productId, Pageable pageable);
}
