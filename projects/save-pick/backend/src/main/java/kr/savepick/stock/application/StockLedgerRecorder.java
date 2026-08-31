package kr.savepick.stock.application;

import java.time.LocalDateTime;
import kr.savepick.common.audit.ActorType;
import kr.savepick.stock.domain.ProductStockRepository;
import kr.savepick.stock.domain.StockChangeReason;
import kr.savepick.stock.domain.StockLedger;
import kr.savepick.stock.domain.StockLedgerRepository;
import kr.savepick.stock.domain.StockQuantities;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 14-project-structure.md §5 강제 규칙 1 — {@code product_stocks}를 UPDATE하는 모든 경로는
 * 이 클래스를 반드시 통과한다. 원장 없이 수량만 바뀌는 경로를 만들지 않는다 (05 C4, 13번 §1).
 * 호출자는 {@code product_stocks} 행을 이미 {@code FOR UPDATE}로 잠근 뒤 이 메서드를 호출해야 한다.
 */
@Service
public class StockLedgerRecorder {

    private final ProductStockRepository productStockRepository;
    private final StockLedgerRepository stockLedgerRepository;

    public StockLedgerRecorder(ProductStockRepository productStockRepository, StockLedgerRepository stockLedgerRepository) {
        this.productStockRepository = productStockRepository;
        this.stockLedgerRepository = stockLedgerRepository;
    }

    @Transactional
    public StockQuantities record(
            Long productId,
            StockQuantities before,
            StockChangeReason reason,
            int deltaTotal,
            int deltaHeld,
            int deltaConfirmed,
            int deltaDiscarded,
            Long orderId,
            ActorType actorType,
            Long actorId,
            String note,
            LocalDateTime now) {

        int affected = productStockRepository.applyDelta(productId, deltaTotal, deltaHeld, deltaConfirmed, deltaDiscarded, now);
        if (affected != 1) {
            throw new IllegalStateException("product_stocks 갱신에 실패했습니다 — productId=" + productId);
        }

        StockQuantities after = before.applyDelta(deltaTotal, deltaHeld, deltaConfirmed, deltaDiscarded);

        StockLedger ledger = StockLedger.record(
                productId, orderId, reason,
                deltaTotal, deltaHeld, deltaConfirmed, deltaDiscarded,
                after.total(), after.available(), after.held(), after.confirmed(),
                actorType, actorId, note, now);
        stockLedgerRepository.save(ledger);

        return after;
    }
}
