package kr.savepick.cart.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.savepick.cart.application.CartValidationService.CartItemEvaluation;
import kr.savepick.cart.domain.Cart;
import kr.savepick.cart.domain.CartItem;
import kr.savepick.cart.domain.CartItemRepository;
import kr.savepick.cart.domain.CartLimitPolicy;
import kr.savepick.cart.domain.CartRepository;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
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
 * API-013~016 장바구니 담기·수량 변경·삭제 (11-api-spec.md §3, BR-009, BR-010, BR-030).
 * 재고는 어떤 경로로도 변경하지 않는다(BR-010) — {@code stock} 도메인은 조회만 한다.
 */
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final StockQueryService stockQueryService;
    private final CartValidationService cartValidationService;
    private final ServerClock serverClock;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            StockQueryService stockQueryService,
            CartValidationService cartValidationService,
            ServerClock serverClock) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.stockQueryService = stockQueryService;
        this.cartValidationService = cartValidationService;
        this.serverClock = serverClock;
    }

    /** API-013. 같은 상품을 다시 담으면 수량을 합산한다(BR-009). 재고는 선점하지 않는다(BR-010). */
    @Transactional
    public AddItemResult addItem(CartOwner owner, Long productId, int quantity) {
        LocalDateTime now = serverClock.now();
        Product product = productRepository.findById(productId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        assertOnSaleAndOpen(product, now);

        Cart cart = resolveOrCreateCart(owner, now);
        Optional<CartItem> existing = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId);
        int mergedQuantity = existing.map(item -> (int) item.getQuantity()).orElse(0) + quantity;

        assertWithinMaxOrderQuantity(mergedQuantity, product);
        assertWithinAvailableStock(mergedQuantity, productId);
        if (existing.isEmpty()) {
            long itemCountAfterAdd = cartItemRepository.countByCartId(cart.getId()) + 1;
            if (!CartLimitPolicy.isWithinItemLimit((int) itemCountAfterAdd)) {
                throw new BusinessException(ErrorCode.CART_ITEM_LIMIT_EXCEEDED);
            }
        }

        int currentPrice = currentPriceOf(product, now);
        CartItem item;
        if (existing.isPresent()) {
            item = existing.get();
            item.changeQuantity((short) mergedQuantity, currentPrice, now);
        } else {
            item = CartItem.add(cart.getId(), productId, (short) mergedQuantity, currentPrice, now);
        }
        item = cartItemRepository.save(item);

        cart.touch(now);
        cartRepository.save(cart);

        long itemCount = cartItemRepository.countByCartId(cart.getId());
        return new AddItemResult(item.getId(), productId, mergedQuantity, currentPrice, (int) itemCount, cart.getGuestToken());
    }

    /** API-014. {@code quantity = 0}이면 품목을 삭제한다(FR-017). */
    @Transactional
    public ChangeQuantityResult changeQuantity(CartOwner owner, Long cartItemId, int quantity) {
        LocalDateTime now = serverClock.now();
        Cart cart = findOwnedCart(owner);
        CartItem item = findOwnedItem(cart, cartItemId);

        if (quantity == 0) {
            cartItemRepository.delete(item);
            cart.touch(now);
            cartRepository.save(cart);
            return ChangeQuantityResult.ofDeleted();
        }

        Product product = productRepository.findById(item.getProductId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        assertWithinMaxOrderQuantity(quantity, product);
        assertWithinAvailableStock(quantity, item.getProductId());

        int currentPrice = currentPriceOf(product, now);
        item.changeQuantity((short) quantity, currentPrice, now);
        item = cartItemRepository.save(item);
        cart.touch(now);
        cartRepository.save(cart);

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        List<CartItemEvaluation> evaluations = cartValidationService.evaluate(items);
        int lineAmount = currentPrice * quantity;
        int totalAmount = evaluations.stream().mapToInt(CartItemEvaluation::lineAmount).sum();
        return ChangeQuantityResult.updated(item.getId(), quantity, lineAmount, totalAmount);
    }

    /** API-015. */
    @Transactional
    public void removeItem(CartOwner owner, Long cartItemId) {
        LocalDateTime now = serverClock.now();
        Cart cart = findOwnedCart(owner);
        CartItem item = findOwnedItem(cart, cartItemId);
        cartItemRepository.delete(item);
        cart.touch(now);
        cartRepository.save(cart);
    }

    /** API-016. */
    @Transactional
    public RemoveUnavailableResult removeUnavailableItems(CartOwner owner) {
        LocalDateTime now = serverClock.now();
        Cart cart = findOwnedCart(owner);
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        List<CartItemEvaluation> evaluations = cartValidationService.evaluate(items);

        Map<Long, CartItem> itemsById = items.stream().collect(java.util.stream.Collectors.toMap(CartItem::getId, i -> i));
        List<Long> removedIds = evaluations.stream()
                .filter(evaluation -> !evaluation.purchasable())
                .map(CartItemEvaluation::cartItemId)
                .toList();
        removedIds.forEach(id -> cartItemRepository.delete(itemsById.get(id)));

        cart.touch(now);
        cartRepository.save(cart);

        List<CartItem> remainingItems = cartItemRepository.findByCartId(cart.getId());
        List<CartItemEvaluation> remainingEvaluations = cartValidationService.evaluate(remainingItems);
        int totalAmount = remainingEvaluations.stream().mapToInt(CartItemEvaluation::lineAmount).sum();
        boolean orderable = !remainingEvaluations.isEmpty() && remainingEvaluations.stream().allMatch(CartItemEvaluation::purchasable);
        return new RemoveUnavailableResult(removedIds, remainingItems.size(), totalAmount, orderable);
    }

    private Cart resolveOrCreateCart(CartOwner owner, LocalDateTime now) {
        if (owner.isGuest()) {
            return cartRepository.findByGuestToken(owner.guestToken())
                    .orElseGet(() -> cartRepository.save(Cart.forGuest(owner.guestToken(), now)));
        }
        return cartRepository.findByMemberId(owner.memberId())
                .orElseGet(() -> cartRepository.save(Cart.forMember(owner.memberId(), now)));
    }

    private Cart findOwnedCart(CartOwner owner) {
        Optional<Cart> cartOpt = owner.isGuest()
                ? cartRepository.findByGuestToken(owner.guestToken())
                : cartRepository.findByMemberId(owner.memberId());
        return cartOpt.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private CartItem findOwnedItem(Cart cart, Long cartItemId) {
        return cartItemRepository.findById(cartItemId)
                .filter(item -> item.getCartId().equals(cart.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** API-013 전용 — HIDDEN·DRAFT는 PRODUCT_NOT_ON_SALE, 마감 시각 경과는 PRODUCT_CLOSED (BR-030). */
    private void assertOnSaleAndOpen(Product product, LocalDateTime now) {
        if (product.getStatus() == ProductStatus.CLOSED || DiscountRatePolicy.isClosed(product.getClosingAt(), now)) {
            throw new BusinessException(ErrorCode.PRODUCT_CLOSED);
        }
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }
    }

    private void assertWithinMaxOrderQuantity(int quantity, Product product) {
        if (!CartLimitPolicy.isWithinMaxOrderQuantity(quantity, product.getMaxOrderQuantity())) {
            throw new BusinessException(
                    ErrorCode.MAX_QUANTITY_EXCEEDED, ErrorCode.MAX_QUANTITY_EXCEEDED.defaultMessage(),
                    Map.of("maxOrderQuantity", product.getMaxOrderQuantity()));
        }
    }

    private void assertWithinAvailableStock(int quantity, Long productId) {
        StockQuantities stock = stockQueryService.getCorrectedQuantity(productId).orElse(StockQuantities.zero());
        if (quantity > stock.available()) {
            throw new BusinessException(
                    ErrorCode.OUT_OF_STOCK, ErrorCode.OUT_OF_STOCK.defaultMessage(),
                    Map.of("available", stock.available()));
        }
    }

    private int currentPriceOf(Product product, LocalDateTime now) {
        if (DiscountRatePolicy.isClosed(product.getClosingAt(), now)) {
            return DiscountRatePolicy.discountPrice(product.getOriginalPrice(), 70);
        }
        int rate = DiscountRatePolicy.discountRate(product.getClosingAt(), now);
        return DiscountRatePolicy.discountPrice(product.getOriginalPrice(), rate);
    }

    public record AddItemResult(
            Long cartItemId, Long productId, int quantity, int currentPrice, int cartItemCount, java.util.UUID guestToken) {
    }

    public record ChangeQuantityResult(boolean deleted, Long cartItemId, int quantity, int lineAmount, int totalAmount) {
        public static ChangeQuantityResult ofDeleted() {
            return new ChangeQuantityResult(true, null, 0, 0, 0);
        }

        public static ChangeQuantityResult updated(Long cartItemId, int quantity, int lineAmount, int totalAmount) {
            return new ChangeQuantityResult(false, cartItemId, quantity, lineAmount, totalAmount);
        }
    }

    public record RemoveUnavailableResult(List<Long> removedCartItemIds, int remainingItemCount, int totalAmount, boolean orderable) {
    }
}
