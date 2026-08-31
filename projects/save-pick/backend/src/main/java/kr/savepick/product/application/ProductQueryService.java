package kr.savepick.product.application;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.domain.DiscountRatePolicy;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductChangeLog;
import kr.savepick.product.domain.ProductRepository;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.product.infrastructure.ProductChangeLogJpaRepository;
import kr.savepick.product.infrastructure.ProductJpaRepository;
import kr.savepick.stock.application.StockQueryService;
import kr.savepick.stock.domain.StockQuantities;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-010·011·102·104·107 상품 조회 (BR-004, BR-006, BR-030). 잔여수량은 stock 도메인의
 * 애플리케이션 서비스({@link StockQueryService})를 통해서만 읽는다 — stock의 엔티티나 JPA
 * 리포지토리를 직접 참조하지 않는다.
 *
 * <p>목록 조회는 시각에 따라 달라지는 할인율·잔여수량으로 정렬·필터링해야 하므로 ON_SALE 상품을
 * DB에서 읽어온 뒤 애플리케이션 계층에서 계산·정렬·페이지를 적용한다 (PS-U3 임시 채택 — 단일
 * 매장 규모의 카탈로그를 전제한다).
 */
@Service
public class ProductQueryService {

    private final ProductRepository productRepository;
    private final ProductJpaRepository productJpaRepository;
    private final ProductChangeLogJpaRepository productChangeLogJpaRepository;
    private final StockQueryService stockQueryService;
    private final MemberRepository memberRepository;
    private final ServerClock serverClock;

    public ProductQueryService(
            ProductRepository productRepository,
            ProductJpaRepository productJpaRepository,
            ProductChangeLogJpaRepository productChangeLogJpaRepository,
            StockQueryService stockQueryService,
            MemberRepository memberRepository,
            ServerClock serverClock) {
        this.productRepository = productRepository;
        this.productJpaRepository = productJpaRepository;
        this.productChangeLogJpaRepository = productChangeLogJpaRepository;
        this.stockQueryService = stockQueryService;
        this.memberRepository = memberRepository;
        this.serverClock = serverClock;
    }

    /** API-010. */
    @Transactional
    public ProductListResult getPublicList(String keyword, ProductSort sort, boolean hideSoldOut, int page, int size) {
        LocalDateTime now = serverClock.now();
        String trimmedKeyword = keyword == null ? "" : keyword.trim();

        List<Product> candidates = trimmedKeyword.isEmpty()
                ? productJpaRepository.findByStatus(ProductStatus.ON_SALE)
                : productJpaRepository.findByStatusAndNameContainingIgnoreCase(ProductStatus.ON_SALE, trimmedKeyword);

        List<Product> stillOnSale = closeDueProducts(candidates, now);

        Map<Long, StockQuantities> quantities =
                stockQueryService.getCorrectedQuantities(stillOnSale.stream().map(Product::getId).toList());

        List<PublicListItem> items = stillOnSale.stream()
                .map(product -> toPublicListItem(product, quantities.getOrDefault(product.getId(), StockQuantities.zero()), now))
                .filter(item -> !hideSoldOut || !item.soldOut())
                .sorted(comparatorFor(sort))
                .toList();

        List<PublicListItem> paged = paginate(items, page, size);
        return new ProductListResult(paged, page, size, items.size(), now);
    }

    /** API-011. */
    @Transactional
    public PublicDetailResult getPublicDetail(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (product.getStatus() == ProductStatus.DRAFT) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        LocalDateTime now = serverClock.now();
        if (product.closeIfDue(now)) {
            productRepository.save(product);
        }
        if (product.getStatus() == ProductStatus.CLOSED) {
            throw new BusinessException(
                    ErrorCode.PRODUCT_CLOSED,
                    ErrorCode.PRODUCT_CLOSED.defaultMessage(),
                    Map.of("name", product.getName(), "closingAt", product.getClosingAt().toString()));
        }
        if (product.getStatus() == ProductStatus.HIDDEN) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }

        StockQuantities quantities = stockQueryService.getCorrectedQuantity(productId).orElse(StockQuantities.zero());
        DiscountView discount = discountViewOf(product, now);
        boolean purchasable = product.getStatus() == ProductStatus.ON_SALE
                && product.getClosingAt().isAfter(now)
                && quantities.available() > 0;

        return new PublicDetailResult(product, discount, quantities, purchasable, now);
    }

    /** API-102. */
    @Transactional
    public AdminListResult getAdminList(ProductStatus statusFilter, int page, int size) {
        LocalDateTime now = serverClock.now();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "closingAt"));
        Page<Product> pageResult = statusFilter != null
                ? productJpaRepository.findByStatus(statusFilter, pageable)
                : productJpaRepository.findAll(pageable);

        List<Product> content = closeDueProducts(pageResult.getContent(), now);
        Map<Long, StockQuantities> quantities = stockQueryService.getCorrectedQuantities(content.stream().map(Product::getId).toList());

        List<AdminListItem> items = content.stream()
                .map(product -> toAdminListItem(product, quantities.getOrDefault(product.getId(), StockQuantities.zero()), now))
                .toList();

        return new AdminListResult(items, page, size, pageResult.getTotalElements(), now);
    }

    /** API-104. */
    @Transactional
    public AdminDetailResult getAdminDetail(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        LocalDateTime now = serverClock.now();
        if (product.closeIfDue(now)) {
            productRepository.save(product);
        }
        StockQuantities quantities = stockQueryService.getCorrectedQuantity(productId).orElse(StockQuantities.zero());
        DiscountView discount = discountViewOf(product, now);
        return new AdminDetailResult(product, discount, quantities);
    }

    /** API-107. */
    @Transactional(readOnly = true)
    public ChangeLogResult getChangeLogs(Long productId, int page, int size) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Page<ProductChangeLog> pageResult = productChangeLogJpaRepository.findByProductIdOrderByChangedAtDesc(
                productId, PageRequest.of(page, size));

        Map<Long, String> actorNames = new HashMap<>();
        for (ProductChangeLog log : pageResult.getContent()) {
            actorNames.computeIfAbsent(log.getActorId(), id -> memberRepository.findById(id).map(Member::getName).orElse("탈퇴한 관리자"));
        }

        List<ChangeLogItem> items = pageResult.getContent().stream()
                .map(log -> new ChangeLogItem(
                        log.getChangedField(), log.getBeforeValue(), log.getAfterValue(),
                        actorNames.get(log.getActorId()), log.getChangedAt()))
                .toList();

        return new ChangeLogResult(items, page, size, pageResult.getTotalElements());
    }

    private List<Product> closeDueProducts(List<Product> products, LocalDateTime now) {
        List<Product> result = new ArrayList<>();
        for (Product product : products) {
            if (product.closeIfDue(now)) {
                productRepository.save(product);
                result.add(product);
            } else {
                result.add(product);
            }
        }
        return result;
    }

    private PublicListItem toPublicListItem(Product product, StockQuantities quantities, LocalDateTime now) {
        DiscountView discount = discountViewOf(product, now);
        int available = quantities.available();
        return new PublicListItem(
                product.getId(), product.getName(), product.getSaleUnit(), product.getOriginalPrice(),
                discount.rate(), discount.price(), available, available <= 5, available <= 0,
                product.getClosingAt(), discount.nextAt());
    }

    private AdminListItem toAdminListItem(Product product, StockQuantities quantities, LocalDateTime now) {
        DiscountView discount = discountViewOf(product, now);
        return new AdminListItem(
                product.getId(), product.getName(), product.getStatus(), product.getOriginalPrice(),
                discount.rate(), discount.price(), discount.nextRate(), discount.nextAt(),
                product.getClosingAt(), quantities.total(), quantities.available());
    }

    /** DRAFT·CLOSED 등 판정이 불가한 경우도 안전하게 처리하는 할인 계산 래퍼. */
    private DiscountView discountViewOf(Product product, LocalDateTime now) {
        if (DiscountRatePolicy.isClosed(product.getClosingAt(), now)) {
            int rate = 70;
            return new DiscountView(rate, DiscountRatePolicy.discountPrice(product.getOriginalPrice(), rate), null, null);
        }
        int rate = DiscountRatePolicy.discountRate(product.getClosingAt(), now);
        int price = DiscountRatePolicy.discountPrice(product.getOriginalPrice(), rate);
        return DiscountRatePolicy.nextDiscount(product.getClosingAt(), now)
                .map(next -> new DiscountView(rate, price, next.rate(), next.at()))
                .orElseGet(() -> new DiscountView(rate, price, null, null));
    }

    private Comparator<PublicListItem> comparatorFor(ProductSort sort) {
        return switch (sort) {
            case CLOSING_SOON -> Comparator.comparing(PublicListItem::closingAt);
            case DISCOUNT_DESC -> Comparator.comparing(PublicListItem::discountRate, Comparator.reverseOrder());
            case PRICE_ASC -> Comparator.comparing(PublicListItem::discountPrice);
        };
    }

    private <T> List<T> paginate(List<T> items, int page, int size) {
        int from = Math.min(page * size, items.size());
        int to = Math.min(from + size, items.size());
        return items.subList(from, to);
    }

    public record DiscountView(int rate, int price, Integer nextRate, LocalDateTime nextAt) {
    }

    public record PublicListItem(
            Long productId, String name, String saleUnit, int originalPrice,
            int discountRate, int discountPrice, int availableQuantity, boolean lowStock, boolean soldOut,
            LocalDateTime closingAt, LocalDateTime nextDiscountAt) {
    }

    public record ProductListResult(List<PublicListItem> items, int number, int size, long totalElements, LocalDateTime serverTime) {
    }

    public record PublicDetailResult(
            Product product, DiscountView discount, StockQuantities quantities, boolean purchasable, LocalDateTime serverTime) {
    }

    public record AdminListItem(
            Long productId, String name, ProductStatus status, int originalPrice,
            int currentDiscountRate, int currentPrice, Integer nextDiscountRate, LocalDateTime nextDiscountAt,
            LocalDateTime closingAt, int totalQuantity, int availableQuantity) {
    }

    public record AdminListResult(List<AdminListItem> items, int number, int size, long totalElements, LocalDateTime serverTime) {
    }

    public record AdminDetailResult(Product product, DiscountView discount, StockQuantities quantities) {
    }

    public record ChangeLogItem(String changedField, String beforeValue, String afterValue, String actorName, LocalDateTime changedAt) {
    }

    public record ChangeLogResult(List<ChangeLogItem> items, int number, int size, long totalElements) {
    }
}
