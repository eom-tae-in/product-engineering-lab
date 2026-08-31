package kr.savepick.cart.application;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.savepick.cart.domain.Cart;
import kr.savepick.cart.domain.CartItem;
import kr.savepick.cart.domain.CartItemRepository;
import kr.savepick.cart.domain.CartRepository;
import kr.savepick.cart.domain.UnavailableReason;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.domain.DiscountRatePolicy;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductRepository;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.stock.application.StockQueryService;
import kr.savepick.stock.domain.StockQuantities;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-012 장바구니 조회 시 재검증 (11-api-spec.md §3, BR-005, BR-006, BR-009, BR-030).
 * 담긴 수량·가격은 저장된 값을 그대로 믿지 않고, 조회할 때마다 product·stock 도메인의
 * 애플리케이션 서비스를 통해 다시 계산한다 — 두 도메인의 엔티티나 JPA 리포지토리를 직접 참조하지
 * 않는다 (14-project-structure.md §4.1 cart ──► product 읽기).
 *
 * <p>{@link #evaluate(List)}는 이 서비스만이 아니라 {@code CartService}의 API-016(구매 불가 품목
 * 일괄 삭제)·수량 변경 후 합계 재계산에서도 공유해서 쓴다 — 재검증 규칙이 두 곳에서 따로
 * 구현되지 않게 한다.
 */
@Service
public class CartValidationService {

    private static final int CLOSED_DISPLAY_RATE = 70;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final StockQueryService stockQueryService;
    private final ServerClock serverClock;

    public CartValidationService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            StockQueryService stockQueryService,
            ServerClock serverClock) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.stockQueryService = stockQueryService;
        this.serverClock = serverClock;
    }

    /** API-012. 장바구니가 아직 없는 손님·회원은 빈 장바구니로 취급한다(오류가 아니다). */
    @Transactional(readOnly = true)
    public CartView getCart(CartOwner owner) {
        LocalDateTime now = serverClock.now();
        Optional<Cart> cartOpt = findCart(owner);
        if (cartOpt.isEmpty()) {
            UUID guestToken = owner.isGuest() ? owner.guestToken() : null;
            return new CartView(guestToken, List.of(), 0, false, now);
        }

        Cart cart = cartOpt.get();
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        List<CartItemEvaluation> evaluations = evaluate(items);
        return new CartView(cart.getGuestToken(), evaluations, totalAmountOf(evaluations), orderableOf(evaluations), now);
    }

    /**
     * 장바구니 품목 목록을 현재 시각 기준으로 재계산한다. 가격 변동(priceChanged)·부족 수량
     * (shortage)·구매 가능 여부(purchasable)·불가 사유(unavailableReason)를 함께 판정한다.
     */
    @Transactional(readOnly = true)
    public List<CartItemEvaluation> evaluate(List<CartItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = serverClock.now();
        List<Long> productIds = items.stream().map(CartItem::getProductId).distinct().toList();

        Map<Long, Product> products = new HashMap<>();
        for (Long productId : productIds) {
            productRepository.findById(productId).ifPresent(product -> products.put(productId, product));
        }
        Map<Long, StockQuantities> quantities = stockQueryService.getCorrectedQuantities(productIds);

        return items.stream()
                .map(item -> evaluateItem(
                        item, products.get(item.getProductId()),
                        quantities.getOrDefault(item.getProductId(), StockQuantities.zero()), now))
                .toList();
    }

    int totalAmountOf(List<CartItemEvaluation> evaluations) {
        return evaluations.stream().mapToInt(CartItemEvaluation::lineAmount).sum();
    }

    boolean orderableOf(List<CartItemEvaluation> evaluations) {
        return !evaluations.isEmpty() && evaluations.stream().allMatch(CartItemEvaluation::purchasable);
    }

    private Optional<Cart> findCart(CartOwner owner) {
        return owner.isGuest() ? cartRepository.findByGuestToken(owner.guestToken()) : cartRepository.findByMemberId(owner.memberId());
    }

    private CartItemEvaluation evaluateItem(CartItem item, Product product, StockQuantities stock, LocalDateTime now) {
        if (product == null) {
            // FK로 보장되는 값이라 실무에서는 도달하지 않는다 — 방어적으로만 처리한다.
            return new CartItemEvaluation(
                    item.getId(), item.getProductId(), "", item.getQuantity(), item.getAddedPrice(), item.getAddedPrice(),
                    false, 0, 0, item.getQuantity(), false, UnavailableReason.PRODUCT_NOT_ON_SALE);
        }

        boolean closed = product.getStatus() == ProductStatus.CLOSED || DiscountRatePolicy.isClosed(product.getClosingAt(), now);
        int currentPrice = closed
                ? DiscountRatePolicy.discountPrice(product.getOriginalPrice(), CLOSED_DISPLAY_RATE)
                : DiscountRatePolicy.discountPrice(
                        product.getOriginalPrice(), DiscountRatePolicy.discountRate(product.getClosingAt(), now));

        int available = stock.available();
        int shortage = Math.max(0, item.getQuantity() - available);

        UnavailableReason reason;
        if (closed) {
            reason = UnavailableReason.PRODUCT_CLOSED;
        } else if (product.getStatus() != ProductStatus.ON_SALE) {
            reason = UnavailableReason.PRODUCT_NOT_ON_SALE;
        } else if (shortage > 0) {
            reason = UnavailableReason.OUT_OF_STOCK;
        } else {
            reason = null;
        }

        int lineAmount = currentPrice * item.getQuantity();
        boolean priceChanged = currentPrice != item.getAddedPrice();

        return new CartItemEvaluation(
                item.getId(), item.getProductId(), product.getName(), item.getQuantity(), item.getAddedPrice(),
                currentPrice, priceChanged, lineAmount, available, shortage, reason == null, reason);
    }

    public record CartItemEvaluation(
            Long cartItemId, Long productId, String name, int quantity, int addedPrice, int currentPrice,
            boolean priceChanged, int lineAmount, int availableQuantity, int shortage, boolean purchasable,
            UnavailableReason unavailableReason) {
    }

    public record CartView(UUID guestToken, List<CartItemEvaluation> items, int totalAmount, boolean orderable, LocalDateTime serverTime) {
    }
}
