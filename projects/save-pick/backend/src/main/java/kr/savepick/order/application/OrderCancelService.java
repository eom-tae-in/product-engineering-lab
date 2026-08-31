package kr.savepick.order.application;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import kr.savepick.common.audit.ActorType;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.order.domain.CancelDeadlinePolicy;
import kr.savepick.order.domain.Order;
import kr.savepick.order.domain.OrderItem;
import kr.savepick.order.domain.OrderItemRepository;
import kr.savepick.order.domain.OrderRepository;
import kr.savepick.order.domain.OrderStatus;
import kr.savepick.order.domain.OrderStatusHistory;
import kr.savepick.order.domain.OrderStatusHistoryRepository;
import kr.savepick.pickup.application.PickupSlotReserveService;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductRepository;
import kr.savepick.stock.application.StockLedgerRecorder;
import kr.savepick.stock.domain.ProductStock;
import kr.savepick.stock.domain.ProductStockRepository;
import kr.savepick.stock.domain.StockChangeReason;
import kr.savepick.stock.domain.StockQuantities;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-025 고객 취소·API-117 관리자 취소 (11-api-spec.md §5·9, 13번 §5, BR-018~020·024).
 * 락 순서(13번 §7.1): {@code orders} → {@code product_stocks}(product_id 오름차순) →
 * {@code pickup_slots}. 부분 취소는 없다(BR-024) — 품목 전체를 한 트랜잭션에서 처리한다.
 */
@Service
public class OrderCancelService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;
    private final StockLedgerRecorder stockLedgerRecorder;
    private final PickupSlotReserveService pickupSlotReserveService;
    private final ServerClock serverClock;

    public OrderCancelService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            ProductRepository productRepository,
            ProductStockRepository productStockRepository,
            StockLedgerRecorder stockLedgerRecorder,
            PickupSlotReserveService pickupSlotReserveService,
            ServerClock serverClock) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.productRepository = productRepository;
        this.productStockRepository = productStockRepository;
        this.stockLedgerRecorder = stockLedgerRecorder;
        this.pickupSlotReserveService = pickupSlotReserveService;
        this.serverClock = serverClock;
    }

    /** API-025 — 본인 확인, 마감 시각(BR-018) 검사를 거친다. */
    @Transactional
    public CancelResult cancelByCustomer(Long orderId, Long memberId) {
        LocalDateTime now = serverClock.now();
        Order order = orderRepository.findByIdAndMemberIdForUpdate(orderId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        rejectIfNotCancelableStatus(order);
        if (!CancelDeadlinePolicy.isCancelable(now, order.getCancelableUntil())) {
            throw new BusinessException(
                    ErrorCode.CANCEL_DEADLINE_PASSED, ErrorCode.CANCEL_DEADLINE_PASSED.defaultMessage(),
                    Map.of("cancelableUntil", String.valueOf(order.getCancelableUntil())));
        }

        return doCancel(order, ActorType.CUSTOMER, memberId, "CUSTOMER", null, now);
    }

    /** API-117 — 사유 필수(BR-020), 고객 취소 마감 시각과 무관하게 실행한다. */
    @Transactional
    public CancelResult cancelByAdmin(Long orderId, Long adminId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.CANCEL_REASON_REQUIRED);
        }
        LocalDateTime now = serverClock.now();
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        rejectIfNotCancelableStatus(order);

        return doCancel(order, ActorType.ADMIN, adminId, "ADMIN", reason, now);
    }

    private void rejectIfNotCancelableStatus(Order order) {
        if (order.getStatus() == OrderStatus.PENDING) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_STATUS, ErrorCode.INVALID_ORDER_STATUS.defaultMessage(),
                    Map.of("currentStatus", order.getStatus().name()));
        }
        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.READY) {
            throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED);
        }
    }

    private CancelResult doCancel(Order order, ActorType actorType, Long actorId, String canceledBy, String reason, LocalDateTime now) {
        OrderStatus fromStatus = order.getStatus();
        int affected = orderRepository.transitionToCanceled(order.getId(), canceledBy, reason, now);
        if (affected != 1) {
            // 이 트랜잭션이 orders 행을 계속 잠근 채로 여기까지 왔으므로 통상 발생하지 않는다
            // (13번 §7.1) — 방어적으로만 남긴다.
            throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED);
        }

        List<OrderItem> items = new ArrayList<>(orderItemRepository.findByOrderId(order.getId()));
        items.sort(Comparator.comparing(OrderItem::getProductId));

        List<StockResult> stockResults = new ArrayList<>();
        for (OrderItem item : items) {
            stockResults.add(restoreOrDiscard(order, item, actorType, actorId, now));
        }

        boolean slotReleased = false;
        if (order.getPickupSlotId() != null) {
            pickupSlotReserveService.release(order.getPickupSlotId());
            slotReleased = true;
        }

        orderRepository.markStockSettled(order.getId(), now);
        orderStatusHistoryRepository.save(OrderStatusHistory.record(
                order.getId(), fromStatus, OrderStatus.CANCELED, actorType, actorId, reason, now));

        return new CancelResult(order.getId(), canceledBy, reason, now, slotReleased, stockResults);
    }

    /** BR-019 — 판정 기준은 스냅샷이 아니라 현행 {@code products.closing_at}이다(10번 T-A7). */
    private StockResult restoreOrDiscard(Order order, OrderItem item, ActorType actorType, Long actorId, LocalDateTime now) {
        Product product = productRepository.findById(item.getProductId())
                .orElseThrow(() -> new IllegalStateException("취소 대상 품목의 상품이 없습니다 — productId=" + item.getProductId()));
        ProductStock stock = productStockRepository.findByProductIdForUpdate(item.getProductId())
                .orElseThrow(() -> new IllegalStateException("취소 대상 품목의 재고 행이 없습니다 — productId=" + item.getProductId()));
        StockQuantities quantities = StockQuantities.of(stock);
        int quantity = item.getQuantity();

        boolean beforeClosing = now.isBefore(product.getClosingAt());
        if (beforeClosing) {
            stockLedgerRecorder.record(
                    item.getProductId(), quantities, StockChangeReason.CANCEL_RESTORE,
                    0, 0, -quantity, 0, order.getId(), actorType, actorId, null, now);
            return new StockResult(item.getProductId(), quantity, true, StockChangeReason.CANCEL_RESTORE, null);
        }
        stockLedgerRecorder.record(
                item.getProductId(), quantities, StockChangeReason.CANCEL_DISCARD,
                -quantity, 0, -quantity, quantity, order.getId(), actorType, actorId, "상품 마감 시각 경과", now);
        return new StockResult(item.getProductId(), quantity, false, StockChangeReason.CANCEL_DISCARD, "상품 마감 시각 경과");
    }

    public record StockResult(Long productId, int quantity, boolean restored, StockChangeReason reason, String note) {
    }

    public record CancelResult(
            Long orderId, String canceledBy, String cancelReason, LocalDateTime canceledAt, boolean slotReleased,
            List<StockResult> stockResults) {
    }
}
