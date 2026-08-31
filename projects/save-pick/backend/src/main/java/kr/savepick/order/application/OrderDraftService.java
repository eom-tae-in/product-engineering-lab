package kr.savepick.order.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.savepick.account.application.OrderRestrictionService;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.account.domain.OrderPermission;
import kr.savepick.cart.domain.Cart;
import kr.savepick.cart.domain.CartItem;
import kr.savepick.cart.domain.CartItemRepository;
import kr.savepick.cart.domain.CartLimitPolicy;
import kr.savepick.cart.domain.CartRepository;
import kr.savepick.cart.domain.UnavailableReason;
import kr.savepick.common.audit.ActorType;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.order.domain.Order;
import kr.savepick.order.domain.OrderItem;
import kr.savepick.order.domain.OrderItemRepository;
import kr.savepick.order.domain.OrderNumberGenerator;
import kr.savepick.order.domain.OrderNumberSequenceRepository;
import kr.savepick.order.domain.OrderRepository;
import kr.savepick.order.domain.OrderStatus;
import kr.savepick.order.domain.OrderStatusHistory;
import kr.savepick.order.domain.OrderStatusHistoryRepository;
import kr.savepick.product.domain.DiscountRatePolicy;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductRepository;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.stock.application.InventoryHoldService;
import kr.savepick.stock.domain.InventoryHold;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-017 주문서 생성(재고 임시 선점) (11-api-spec.md §4, BR-005·007·008·009·010·023·027·029).
 * 재고 선점 자체는 {@code stock/application/InventoryHoldService}를 그대로 호출한다 — 이 서비스가
 * 새로 만들지 않는다(14-project-structure.md §5).
 *
 * <p>처리 순서(13번 §3을 주문서 생성에 맞게 재구성): 노쇼 제한 → PENDING 중복 → 장바구니 대상
 * 품목 조회·검증 → {@code InventoryHoldService.createHolds} → orders/order_items/이력 기록.
 */
@Service
public class OrderDraftService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderNumberSequenceRepository orderNumberSequenceRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final OrderRestrictionService orderRestrictionService;
    private final InventoryHoldService inventoryHoldService;
    private final ServerClock serverClock;
    private final Duration holdTtl;

    public OrderDraftService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            OrderNumberSequenceRepository orderNumberSequenceRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            MemberRepository memberRepository,
            OrderRestrictionService orderRestrictionService,
            InventoryHoldService inventoryHoldService,
            ServerClock serverClock,
            @Value("${savepick.hold.ttl}") String holdTtl) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.orderNumberSequenceRepository = orderNumberSequenceRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
        this.orderRestrictionService = orderRestrictionService;
        this.inventoryHoldService = inventoryHoldService;
        this.serverClock = serverClock;
        this.holdTtl = Duration.parse(holdTtl);
    }

    @Transactional
    public DraftResult createDraft(Long memberId, List<Long> requestedCartItemIds) {
        LocalDateTime now = serverClock.now();

        // 1. 노쇼 제한 (BR-023)
        OrderRestrictionService.NoShowStatus restriction = orderRestrictionService.getStatus(memberId);
        if (restriction.orderPermission() == OrderPermission.RESTRICTED) {
            throw new BusinessException(
                    ErrorCode.ORDER_RESTRICTED, ErrorCode.ORDER_RESTRICTED.defaultMessage(),
                    Map.of("restrictedUntil", String.valueOf(restriction.restrictedUntil())));
        }

        // 2. 유효한 PENDING 주문서 중복 (BR-007)
        Optional<Order> existingPending = orderRepository.findByMemberIdAndStatus(memberId, OrderStatus.PENDING);
        if (existingPending.isPresent()) {
            throw pendingExists(existingPending.get());
        }

        Member member = memberRepository.findById(memberId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));

        // 3. 장바구니 대상 품목 (BR-010)
        List<DraftLine> lines = resolveDraftLines(memberId, requestedCartItemIds, now);

        int totalAmount = lines.stream().mapToInt(l -> l.unitPrice() * l.quantity()).sum();
        LocalDateTime earliestClosingAt = lines.stream()
                .map(l -> l.product().getClosingAt())
                .min(LocalDateTime::compareTo)
                .orElseThrow();

        String orderNo = OrderNumberGenerator.generate(now.toLocalDate(), orderNumberSequenceRepository.nextValue());
        LocalDateTime placeholderHoldExpiresAt = now.plus(holdTtl);
        Order order = Order.createPending(orderNo, memberId, totalAmount, member.getName(), member.getPhone(), placeholderHoldExpiresAt, now);
        try {
            order = orderRepository.save(order);
        } catch (DataIntegrityViolationException e) {
            Order raced = orderRepository.findByMemberIdAndStatus(memberId, OrderStatus.PENDING).orElse(null);
            if (raced != null) {
                throw pendingExists(raced);
            }
            throw e;
        }

        // 4. 재고 선점 — InventoryHoldService가 락·부족 판정·전량 성공/전량 실패를 전부 처리한다.
        Map<Long, Integer> requestedQuantityByProductId = new LinkedHashMap<>();
        for (DraftLine line : lines) {
            requestedQuantityByProductId.put(line.product().getId(), line.quantity());
        }
        List<InventoryHold> holds = inventoryHoldService.createHolds(order.getId(), requestedQuantityByProductId, ActorType.CUSTOMER, memberId);

        // 5. hold_expires_at을 InventoryHoldService가 실제로 쓴 값과 반드시 같게 맞춘다.
        LocalDateTime actualHoldExpiresAt = holds.get(0).getExpiresAt();
        order.correctHoldExpiresAt(actualHoldExpiresAt, now);
        order = orderRepository.save(order);

        List<OrderItem> savedItems = new ArrayList<>();
        for (DraftLine line : lines) {
            OrderItem item = OrderItem.snapshot(
                    order.getId(), line.product().getId(), line.product().getName(), line.product().getSaleUnit(),
                    (short) line.quantity(), line.product().getOriginalPrice(), (short) line.discountRate(),
                    line.unitPrice(), line.unitPrice() * line.quantity(), line.product().getClosingAt());
            savedItems.add(orderItemRepository.save(item));
        }

        orderStatusHistoryRepository.save(
                OrderStatusHistory.record(order.getId(), null, OrderStatus.PENDING, ActorType.CUSTOMER, memberId, null, now));

        return new DraftResult(order, savedItems, earliestClosingAt, now);
    }

    /** BR-009·030 — 대상 품목의 구매 가능 여부·수량 한도를 검증하고 가격 스냅샷을 계산한다. */
    private List<DraftLine> resolveDraftLines(Long memberId, List<Long> requestedCartItemIds, LocalDateTime now) {
        Cart cart = cartRepository.findByMemberId(memberId).orElseThrow(() -> new BusinessException(ErrorCode.CART_EMPTY));
        List<CartItem> allItems = cartItemRepository.findByCartId(cart.getId());
        List<CartItem> targetItems = (requestedCartItemIds == null || requestedCartItemIds.isEmpty())
                ? allItems
                : allItems.stream().filter(item -> requestedCartItemIds.contains(item.getId())).toList();
        if (targetItems.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        List<DraftLine> lines = new ArrayList<>();
        List<Map<String, Object>> unavailable = new ArrayList<>();
        for (CartItem item : targetItems) {
            Product product = productRepository.findById(item.getProductId()).orElse(null);
            if (product == null) {
                unavailable.add(Map.of("productId", item.getProductId(), "reason", UnavailableReason.PRODUCT_NOT_ON_SALE.name()));
                continue;
            }
            boolean closed = product.getStatus() == ProductStatus.CLOSED || DiscountRatePolicy.isClosed(product.getClosingAt(), now);
            if (closed) {
                unavailable.add(Map.of("productId", product.getId(), "reason", UnavailableReason.PRODUCT_CLOSED.name()));
                continue;
            }
            if (product.getStatus() != ProductStatus.ON_SALE) {
                unavailable.add(Map.of("productId", product.getId(), "reason", UnavailableReason.PRODUCT_NOT_ON_SALE.name()));
                continue;
            }
            if (!CartLimitPolicy.isWithinMaxOrderQuantity(item.getQuantity(), product.getMaxOrderQuantity())) {
                throw new BusinessException(
                        ErrorCode.MAX_QUANTITY_EXCEEDED, ErrorCode.MAX_QUANTITY_EXCEEDED.defaultMessage(),
                        Map.of("productId", product.getId(), "maxOrderQuantity", product.getMaxOrderQuantity()));
            }
            int discountRate = DiscountRatePolicy.discountRate(product.getClosingAt(), now);
            int unitPrice = DiscountRatePolicy.discountPrice(product.getOriginalPrice(), discountRate);
            lines.add(new DraftLine(product, item.getQuantity(), discountRate, unitPrice));
        }

        if (!unavailable.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.CART_HAS_UNAVAILABLE_ITEM, ErrorCode.CART_HAS_UNAVAILABLE_ITEM.defaultMessage(),
                    Map.of("items", unavailable));
        }
        return lines;
    }

    private BusinessException pendingExists(Order existing) {
        return new BusinessException(
                ErrorCode.PENDING_ORDER_EXISTS, ErrorCode.PENDING_ORDER_EXISTS.defaultMessage(),
                Map.of("orderId", existing.getId(), "holdExpiresAt", String.valueOf(existing.getHoldExpiresAt())));
    }

    private record DraftLine(Product product, int quantity, int discountRate, int unitPrice) {
    }

    public record DraftResult(Order order, List<OrderItem> items, LocalDateTime earliestClosingAt, LocalDateTime serverTime) {
    }
}
