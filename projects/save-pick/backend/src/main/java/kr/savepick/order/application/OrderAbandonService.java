package kr.savepick.order.application;

import java.time.LocalDateTime;
import java.util.Map;
import kr.savepick.common.audit.ActorType;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.order.domain.Order;
import kr.savepick.order.domain.OrderRepository;
import kr.savepick.order.domain.OrderStatus;
import kr.savepick.order.domain.OrderStatusHistory;
import kr.savepick.order.domain.OrderStatusHistoryRepository;
import kr.savepick.stock.application.InventoryHoldService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-019 주문서 포기 (BR-008, 05 §2.2). PENDING만 가능하며, 결제 전 포기는 CANCELED가 아니라
 * EXPIRED로 처리한다(05 A3). 재고 해제는 {@code InventoryHoldService.releaseHolds}를 그대로
 * 호출한다(13번 §4의 "주문서 포기" 행 — HELD → RELEASED, HOLD_RELEASE).
 */
@Service
public class OrderAbandonService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final InventoryHoldService inventoryHoldService;
    private final ServerClock serverClock;

    public OrderAbandonService(
            OrderRepository orderRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            InventoryHoldService inventoryHoldService,
            ServerClock serverClock) {
        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.inventoryHoldService = inventoryHoldService;
        this.serverClock = serverClock;
    }

    @Transactional
    public AbandonResult abandon(Long orderId, Long memberId) {
        LocalDateTime now = serverClock.now();
        // 13번 §7.1 락 순서 1번 — orders를 먼저 잠근 뒤 stock(2번)을 건드린다.
        Order order = orderRepository.findByIdAndMemberIdForUpdate(orderId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_STATUS, ErrorCode.INVALID_ORDER_STATUS.defaultMessage(),
                    Map.of("currentStatus", order.getStatus().name()));
        }

        inventoryHoldService.releaseHolds(orderId, ActorType.CUSTOMER, memberId);
        int affected = orderRepository.expireIfPending(orderId, now);
        if (affected == 1) {
            orderStatusHistoryRepository.save(OrderStatusHistory.record(
                    orderId, OrderStatus.PENDING, OrderStatus.EXPIRED, ActorType.CUSTOMER, memberId, "고객 주문서 포기", now));
        }
        return new AbandonResult(orderId, OrderStatus.EXPIRED, now);
    }

    public record AbandonResult(Long orderId, OrderStatus status, LocalDateTime releasedAt) {
    }
}
