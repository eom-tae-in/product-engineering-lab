package kr.savepick.stock.application;

import java.time.LocalDateTime;
import kr.savepick.common.audit.ActorType;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.domain.ProductRepository;
import kr.savepick.stock.domain.ProductStock;
import kr.savepick.stock.domain.ProductStockRepository;
import kr.savepick.stock.domain.StockChangeReason;
import kr.savepick.stock.domain.StockQuantities;
import kr.savepick.stock.domain.StockReductionPolicy;
import kr.savepick.stock.infrastructure.LockTimeoutGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-109 재고 등록·조정 (BR-006, BR-025, 13번 §6). {@code product} 도메인을 읽기만 한다
 * (14-project-structure.md §4.1 stock ──► product 읽기).
 */
@Service
public class StockAdjustService {

    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;
    private final ExpiredHoldReclaimer expiredHoldReclaimer;
    private final StockLedgerRecorder stockLedgerRecorder;
    private final LockTimeoutGuard lockTimeoutGuard;
    private final ServerClock serverClock;

    public StockAdjustService(
            ProductRepository productRepository,
            ProductStockRepository productStockRepository,
            ExpiredHoldReclaimer expiredHoldReclaimer,
            StockLedgerRecorder stockLedgerRecorder,
            LockTimeoutGuard lockTimeoutGuard,
            ServerClock serverClock) {
        this.productRepository = productRepository;
        this.productStockRepository = productStockRepository;
        this.expiredHoldReclaimer = expiredHoldReclaimer;
        this.stockLedgerRecorder = stockLedgerRecorder;
        this.lockTimeoutGuard = lockTimeoutGuard;
        this.serverClock = serverClock;
    }

    /**
     * @param targetTotal 총 재고의 목표값(절대값). 증감량이 아니다 (11번 API-109).
     */
    @Transactional
    public AdjustResult adjust(Long productId, int targetTotal, String note, Long actorId) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        lockTimeoutGuard.apply();
        LocalDateTime now = serverClock.now();

        ProductStock existing = productStockRepository.findByProductIdForUpdate(productId).orElse(null);
        StockQuantities before = existing == null ? StockQuantities.zero() : StockQuantities.of(existing);
        if (existing != null) {
            before = expiredHoldReclaimer.reclaim(productId, before, now);
        }

        int minimumSettable = StockReductionPolicy.minimumSettableQuantity(before);
        if (!StockReductionPolicy.isTargetAllowed(targetTotal, before)) {
            throw new BusinessException(
                    ErrorCode.STOCK_BELOW_COMMITTED,
                    ErrorCode.STOCK_BELOW_COMMITTED.defaultMessage(),
                    java.util.Map.of("minimumSettableQuantity", minimumSettable));
        }

        int deltaTotal = targetTotal - before.total();
        StockQuantities after = stockLedgerRecorder.record(
                productId, before, StockChangeReason.ADMIN_ADJUST,
                deltaTotal, 0, 0, 0,
                null, ActorType.ADMIN, actorId, note, now);

        return new AdjustResult(before, after, minimumSettable, now);
    }

    public record AdjustResult(StockQuantities before, StockQuantities after, int minimumSettableQuantity, LocalDateTime changedAt) {
    }
}
