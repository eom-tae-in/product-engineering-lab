package kr.savepick.stock.application;

import java.time.LocalDateTime;
import java.util.Optional;
import kr.savepick.stock.domain.ProductStock;
import kr.savepick.stock.infrastructure.ConfirmedSaleQuantityReadDao;
import kr.savepick.stock.infrastructure.InventoryHoldJpaRepository;
import kr.savepick.stock.infrastructure.ProductStockJpaRepository;
import kr.savepick.stock.infrastructure.StockLedgerJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BATCH-04 재고 정합성 점검 (11-api-spec.md §11, 13-inventory-concurrency.md §9.1, FR-046, BR-006).
 * 상품 1건 = 트랜잭션 1개로 집계값과 기대값을 비교만 한다(14-project-structure.md §6.2).
 *
 * <p><b>불일치를 자동으로 보정하지 않는다.</b> 보정은 원인을 지워 버리므로, 이 서비스는
 * {@code @Transactional(readOnly = true)}로 어떤 쓰기도 하지 않고 결과만 돌려준다. 경보를
 * 남기는 것은 호출자({@code stock/batch/StockConsistencyCheckJob})의 몫이고, 값을 되돌릴지는
 * 원장을 보고 사람이 결정한다 (13번 §9.1).
 */
@Service
public class StockConsistencyCheckService {

    private final ProductStockJpaRepository productStockJpaRepository;
    private final InventoryHoldJpaRepository inventoryHoldJpaRepository;
    private final StockLedgerJpaRepository stockLedgerJpaRepository;
    private final ConfirmedSaleQuantityReadDao confirmedSaleQuantityReadDao;

    public StockConsistencyCheckService(
            ProductStockJpaRepository productStockJpaRepository,
            InventoryHoldJpaRepository inventoryHoldJpaRepository,
            StockLedgerJpaRepository stockLedgerJpaRepository,
            ConfirmedSaleQuantityReadDao confirmedSaleQuantityReadDao) {
        this.productStockJpaRepository = productStockJpaRepository;
        this.inventoryHoldJpaRepository = inventoryHoldJpaRepository;
        this.stockLedgerJpaRepository = stockLedgerJpaRepository;
        this.confirmedSaleQuantityReadDao = confirmedSaleQuantityReadDao;
    }

    /**
     * 13번 §9.1 비교표 3줄을 그대로 검사한다({@code available_quantity}는 생성 컬럼이라 항상
     * 성립하므로 검사 대상이 아니다).
     *
     * @return 재고 행이 없는 상품이면 빈 값(점검 대상이 아니다)
     */
    @Transactional(readOnly = true)
    public Optional<ConsistencyCheckResult> check(Long productId, LocalDateTime now) {
        return productStockJpaRepository.findByProductId(productId)
                .map(stock -> compare(stock, now));
    }

    private ConsistencyCheckResult compare(ProductStock stock, LocalDateTime now) {
        Long productId = stock.getProductId();
        int expectedHeld = inventoryHoldJpaRepository.sumActiveHeldByProductId(productId, now);
        int expectedConfirmed = confirmedSaleQuantityReadDao.sumConfirmedSaleQuantity(productId);
        int expectedTotal = stockLedgerJpaRepository.sumDeltaTotalByProductId(productId);
        return new ConsistencyCheckResult(
                productId,
                stock.getHeldQuantity(), expectedHeld,
                stock.getConfirmedQuantity(), expectedConfirmed,
                stock.getTotalQuantity(), expectedTotal);
    }

    /**
     * 실제값(actual)은 {@code product_stocks}의 집계 컬럼, 기대값(expected)은 원장·선점·주문에서
     * 다시 센 값이다.
     */
    public record ConsistencyCheckResult(
            Long productId,
            int actualHeldQuantity, int expectedHeldQuantity,
            int actualConfirmedQuantity, int expectedConfirmedQuantity,
            int actualTotalQuantity, int expectedTotalQuantity) {

        public boolean heldMatches() {
            return actualHeldQuantity == expectedHeldQuantity;
        }

        public boolean confirmedMatches() {
            return actualConfirmedQuantity == expectedConfirmedQuantity;
        }

        public boolean totalMatches() {
            return actualTotalQuantity == expectedTotalQuantity;
        }

        public boolean consistent() {
            return heldMatches() && confirmedMatches() && totalMatches();
        }
    }
}
