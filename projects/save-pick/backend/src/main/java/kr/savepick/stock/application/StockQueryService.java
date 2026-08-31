package kr.savepick.stock.application;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductRepository;
import kr.savepick.product.infrastructure.ProductJpaRepository;
import kr.savepick.stock.domain.ProductStock;
import kr.savepick.stock.domain.StockLedger;
import kr.savepick.stock.domain.StockQuantities;
import kr.savepick.stock.infrastructure.InventoryHoldJpaRepository;
import kr.savepick.stock.infrastructure.OrderNoReadDao;
import kr.savepick.stock.infrastructure.ProductStockJpaRepository;
import kr.savepick.stock.infrastructure.StockLedgerJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-110·API-111 재고 현황·이력 조회 (FR-046, FR-047). 만료됐지만 아직 회수되지 않은 선점을
 * 표시값에서 제외하는 읽기 보정도 이 서비스가 맡는다 (13번 §2.1). 목록 규모가 크지 않은
 * MVP 범위를 전제로 애플리케이션 계층에서 필터·페이지를 적용한다 (PS-U3 임시 채택).
 */
@Service
public class StockQueryService {

    private final ProductStockJpaRepository productStockJpaRepository;
    private final InventoryHoldJpaRepository inventoryHoldJpaRepository;
    private final StockLedgerJpaRepository stockLedgerJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final ProductRepository productRepository;
    private final OrderNoReadDao orderNoReadDao;
    private final ServerClock serverClock;

    public StockQueryService(
            ProductStockJpaRepository productStockJpaRepository,
            InventoryHoldJpaRepository inventoryHoldJpaRepository,
            StockLedgerJpaRepository stockLedgerJpaRepository,
            ProductJpaRepository productJpaRepository,
            ProductRepository productRepository,
            OrderNoReadDao orderNoReadDao,
            ServerClock serverClock) {
        this.productStockJpaRepository = productStockJpaRepository;
        this.inventoryHoldJpaRepository = inventoryHoldJpaRepository;
        this.stockLedgerJpaRepository = stockLedgerJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.productRepository = productRepository;
        this.orderNoReadDao = orderNoReadDao;
        this.serverClock = serverClock;
    }

    /** 13번 §2.1 — 상품별로 만료된 HELD 선점 합계를 뺀 보정 수량. 쓰기를 하지 않는다. */
    @Transactional(readOnly = true)
    public Map<Long, StockQuantities> getCorrectedQuantities(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        LocalDateTime now = serverClock.now();
        Map<Long, Integer> expiredHeldByProduct = expiredHeldSums(productIds, now);

        Map<Long, StockQuantities> result = new HashMap<>();
        for (ProductStock stock : productStockJpaRepository.findAllById(productIds)) {
            StockQuantities raw = StockQuantities.of(stock);
            int expired = expiredHeldByProduct.getOrDefault(stock.getProductId(), 0);
            result.put(stock.getProductId(), raw.withExpiredHeldExcluded(expired));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<StockQuantities> getCorrectedQuantity(Long productId) {
        return getCorrectedQuantities(List.of(productId)).values().stream().findFirst();
    }

    /** 05 §5.2 DRAFT → ON_SALE 전이 조건 — 재고가 1 이상 등록돼 있어야 한다. */
    @Transactional(readOnly = true)
    public boolean isStockRegistered(Long productId) {
        return productStockJpaRepository.findByProductId(productId)
                .map(stock -> stock.getTotalQuantity() >= 1)
                .orElse(false);
    }

    /** API-106 응답의 keptHoldCount — 만료되지 않은 유효 HELD 선점 수량 합계. */
    @Transactional(readOnly = true)
    public int countActiveHeld(Long productId) {
        return inventoryHoldJpaRepository.sumActiveHeldByProductId(productId, serverClock.now());
    }

    /** API-110. */
    @Transactional(readOnly = true)
    public OverviewPage getOverview(boolean onlyUnavailable, int page, int size) {
        LocalDateTime now = serverClock.now();
        List<ProductStock> stocks = productStockJpaRepository.findAll();
        List<Long> productIds = stocks.stream().map(ProductStock::getProductId).toList();
        Map<Long, Integer> expiredHeldByProduct = expiredHeldSums(productIds, now);
        Map<Long, Product> productsById = new HashMap<>();
        for (Product product : productJpaRepository.findAllById(productIds)) {
            productsById.put(product.getId(), product);
        }

        List<OverviewItem> items = stocks.stream()
                .map(stock -> {
                    Product product = productsById.get(stock.getProductId());
                    StockQuantities corrected =
                            StockQuantities.of(stock).withExpiredHeldExcluded(expiredHeldByProduct.getOrDefault(stock.getProductId(), 0));
                    boolean consistent = corrected.total() == corrected.available() + corrected.held() + corrected.confirmed();
                    return new OverviewItem(
                            stock.getProductId(),
                            product == null ? "" : product.getName(),
                            product == null ? null : product.getStatus(),
                            corrected.total(), corrected.available(), corrected.held(), corrected.confirmed(),
                            corrected.discarded(), consistent);
                })
                .filter(item -> !onlyUnavailable || item.availableQuantity() == 0)
                .sorted(Comparator.comparing(OverviewItem::productId))
                .toList();

        return new OverviewPage(paginate(items, page, size), page, size, items.size());
    }

    /** API-111. */
    @Transactional(readOnly = true)
    public LedgerPage getLedger(Long productId, int page, int size) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        var pageResult = stockLedgerJpaRepository.findByProductIdOrderByOccurredAtDesc(
                productId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")));

        List<Long> orderIds = pageResult.getContent().stream()
                .map(StockLedger::getOrderId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> orderNos = orderNoReadDao.findOrderNos(orderIds);

        List<LedgerItem> items = pageResult.getContent().stream()
                .map(ledger -> new LedgerItem(
                        ledger.getId(), ledger.getReason(),
                        ledger.getOrderId() == null ? null : orderNos.get(ledger.getOrderId()),
                        ledger.getDeltaTotal(), ledger.getDeltaHeld(), ledger.getDeltaConfirmed(), ledger.getDeltaDiscarded(),
                        ledger.getAfterTotal(), ledger.getAfterAvailable(), ledger.getAfterHeld(), ledger.getAfterConfirmed(),
                        ledger.getActorType(), ledger.getNote(), ledger.getOccurredAt()))
                .toList();

        return new LedgerPage(items, page, size, pageResult.getTotalElements());
    }

    private Map<Long, Integer> expiredHeldSums(List<Long> productIds, LocalDateTime now) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> result = new HashMap<>();
        for (InventoryHoldJpaRepository.ExpiredHeldRow row : inventoryHoldJpaRepository.sumExpiredHeldByProductIds(productIds, now)) {
            result.put(row.getProductId(), row.getQuantity().intValue());
        }
        return result;
    }

    private <T> List<T> paginate(List<T> items, int page, int size) {
        int from = Math.min(page * size, items.size());
        int to = Math.min(from + size, items.size());
        return items.subList(from, to);
    }

    public record OverviewItem(
            Long productId, String name, kr.savepick.product.domain.ProductStatus status,
            int totalQuantity, int availableQuantity, int heldQuantity, int confirmedQuantity,
            int discardedQuantity, boolean consistent) {
    }

    public record OverviewPage(List<OverviewItem> items, int number, int size, long totalElements) {
    }

    public record LedgerItem(
            Long ledgerId, kr.savepick.stock.domain.StockChangeReason reason, String orderNo,
            int deltaTotal, int deltaHeld, int deltaConfirmed, int deltaDiscarded,
            int afterTotal, int afterAvailable, int afterHeld, int afterConfirmed,
            kr.savepick.common.audit.ActorType actorType, String note, LocalDateTime occurredAt) {
    }

    public record LedgerPage(List<LedgerItem> items, int number, int size, long totalElements) {
    }
}
