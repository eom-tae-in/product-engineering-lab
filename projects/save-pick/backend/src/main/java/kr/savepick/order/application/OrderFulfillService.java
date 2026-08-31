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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-115 픽업 준비 완료, API-116 픽업 완료 (11-api-spec.md §9, BR-020·021). 둘 다 재고를
 * 바꾸지 않는다(05 §6.2 — 이벤트 기재 없음 = 변화 없음).
 */
@Service
public class OrderFulfillService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final ServerClock serverClock;

    public OrderFulfillService(
            OrderRepository orderRepository, OrderStatusHistoryRepository orderStatusHistoryRepository, ServerClock serverClock) {
        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.serverClock = serverClock;
    }

    /** API-115 — CONFIRMED만 가능. */
    @Transactional
    public FulfillResult markReady(Long orderId, Long adminId) {
        LocalDateTime now = serverClock.now();
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_STATUS, ErrorCode.INVALID_ORDER_STATUS.defaultMessage(),
                    Map.of("currentStatus", order.getStatus().name()));
        }
        orderRepository.transitionToReady(orderId, now);
        orderStatusHistoryRepository.save(OrderStatusHistory.record(
                orderId, OrderStatus.CONFIRMED, OrderStatus.READY, ActorType.ADMIN, adminId, null, now));
        return new FulfillResult(orderId, OrderStatus.READY, now);
    }

    /** API-116 — CONFIRMED 또는 READY에서 가능(조건부 UPDATE로 중복 처리를 막는다). */
    @Transactional
    public FulfillResult complete(Long orderId, Long adminId) {
        LocalDateTime now = serverClock.now();
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        OrderStatus fromStatus = order.getStatus();
        if (fromStatus != OrderStatus.CONFIRMED && fromStatus != OrderStatus.READY) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_STATUS, ErrorCode.INVALID_ORDER_STATUS.defaultMessage(),
                    Map.of("currentStatus", fromStatus.name()));
        }
        int affected = orderRepository.transitionToCompleted(orderId, now);
        if (affected != 1) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_STATUS, ErrorCode.INVALID_ORDER_STATUS.defaultMessage(),
                    Map.of("currentStatus", fromStatus.name()));
        }
        orderStatusHistoryRepository.save(OrderStatusHistory.record(
                orderId, fromStatus, OrderStatus.COMPLETED, ActorType.ADMIN, adminId, null, now));
        return new FulfillResult(orderId, OrderStatus.COMPLETED, now);
    }

    public record FulfillResult(Long orderId, OrderStatus status, LocalDateTime occurredAt) {
    }
}
