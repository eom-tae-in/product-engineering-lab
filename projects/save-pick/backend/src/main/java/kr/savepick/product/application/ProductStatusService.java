package kr.savepick.product.application;

import java.time.LocalDateTime;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductChangeLog;
import kr.savepick.product.domain.ProductRepository;
import kr.savepick.product.infrastructure.ConfirmedOrderReadDao;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.product.infrastructure.ProductChangeLogJpaRepository;
import kr.savepick.stock.application.StockQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-106 상품 판매 상태 전환 (05-state-rules.md §5.2·5.3, BR-025, BR-030).
 * DRAFT → ON_SALE 전이는 재고 등록 여부를 확인해야 하므로 stock 도메인을 읽는다. product·stock는
 * 이번 슬라이스에서 서로의 데이터를 함께 필요로 하는 한 쌍으로 다뤄지며(14번 문서의 원칙적
 * 방향은 stock ──► product 읽기만 명시하지만, 이 판정에는 반대 방향 읽기가 불가피하다),
 * 두 도메인 모두 애플리케이션 서비스(StockQueryService)를 통해서만 접근한다 — 엔티티나 JPA
 * 리포지토리를 직접 참조하지 않는다.
 */
@Service
public class ProductStatusService {

    private final ProductRepository productRepository;
    private final ProductChangeLogJpaRepository productChangeLogJpaRepository;
    private final StockQueryService stockQueryService;
    private final ConfirmedOrderReadDao confirmedOrderReadDao;
    private final ServerClock serverClock;

    public ProductStatusService(
            ProductRepository productRepository,
            ProductChangeLogJpaRepository productChangeLogJpaRepository,
            StockQueryService stockQueryService,
            ConfirmedOrderReadDao confirmedOrderReadDao,
            ServerClock serverClock) {
        this.productRepository = productRepository;
        this.productChangeLogJpaRepository = productChangeLogJpaRepository;
        this.stockQueryService = stockQueryService;
        this.confirmedOrderReadDao = confirmedOrderReadDao;
        this.serverClock = serverClock;
    }

    @Transactional
    public StatusChangeResult changeStatus(Long productId, ProductStatus target, Long actorId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        LocalDateTime now = serverClock.now();

        boolean stockRegistered = stockQueryService.isStockRegistered(productId);
        if (!product.canTransitionTo(target, stockRegistered)) {
            throw new BusinessException(ErrorCode.PRODUCT_STATUS_TRANSITION_DENIED);
        }

        ProductStatus before = product.getStatus();
        if (target == ProductStatus.ON_SALE) {
            product.startSale(now);
        } else {
            product.hide(now);
        }
        productChangeLogJpaRepository.save(
                ProductChangeLog.of(productId, "status", before.name(), target.name(), actorId, now));
        productRepository.save(product);

        int keptHoldCount = stockQueryService.countActiveHeld(productId);
        // 05 §5.2 — 이 전환은 기존 HELD 선점과 확정 주문을 취소하지 않고 유지한다.
        // 유지된 확정 주문 수를 실제로 세어 관리자에게 알린다(API-106).
        int keptConfirmedOrderCount = confirmedOrderReadDao.countConfirmedOrders(productId);

        return new StatusChangeResult(product, now, keptHoldCount, keptConfirmedOrderCount);
    }

    public record StatusChangeResult(Product product, LocalDateTime changedAt, int keptHoldCount, int keptConfirmedOrderCount) {
    }
}
