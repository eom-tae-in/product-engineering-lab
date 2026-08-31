package kr.savepick.order.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.order.domain.Order;
import kr.savepick.order.domain.OrderItem;
import kr.savepick.order.domain.OrderItemRepository;
import kr.savepick.order.domain.OrderRepository;
import kr.savepick.order.domain.OrderStatus;
import kr.savepick.pickup.application.PickupSlotQueryService;
import kr.savepick.pickup.application.PickupSlotQueryService.SelectableSlotsResult;
import kr.savepick.pickup.application.PickupSlotQueryService.SlotAssignment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-020 선택 가능한 픽업 시간대 조회, API-021 픽업 시간대 지정 (BR-013·015·016·017).
 * 주문 품목의 최이른 마감 시각·본인 소유 검증은 여기서 하고, 시간대 규칙 판정 자체는
 * {@code pickup/application/PickupSlotQueryService}에 위임한다(14-project-structure.md §4.1
 * order ──► pickup). 이 시점엔 정원을 점유하지 않는다(05 §8, A10).
 */
@Service
public class PickupSlotAssignService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PickupSlotQueryService pickupSlotQueryService;
    private final ServerClock serverClock;

    public PickupSlotAssignService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PickupSlotQueryService pickupSlotQueryService,
            ServerClock serverClock) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.pickupSlotQueryService = pickupSlotQueryService;
        this.serverClock = serverClock;
    }

    /** API-020. */
    @Transactional(readOnly = true)
    public SelectableSlotsResult getSelectableSlots(Long orderId, Long memberId, LocalDate date) {
        LocalDateTime now = serverClock.now();
        Order order = ownedPendingOrder(orderId, memberId, now);
        LocalDateTime earliestClosingAt = earliestClosingAt(order.getId());
        return pickupSlotQueryService.getSelectableSlots(date, earliestClosingAt, now);
    }

    /** API-021. */
    @Transactional
    public AssignResult assign(Long orderId, Long memberId, Long slotId) {
        LocalDateTime now = serverClock.now();
        Order order = ownedPendingOrder(orderId, memberId, now);
        LocalDateTime earliestClosingAt = earliestClosingAt(order.getId());

        SlotAssignment assignment = pickupSlotQueryService.validateForAssignment(slotId, earliestClosingAt, now);
        order.assignPickupSlot(assignment.slotId(), assignment.slotDate(), now);
        order = orderRepository.save(order);

        long holdRemainingSeconds = Math.max(0, java.time.Duration.between(now, order.getHoldExpiresAt()).toSeconds());
        return new AssignResult(order, assignment, holdRemainingSeconds);
    }

    private Order ownedPendingOrder(Long orderId, Long memberId, LocalDateTime now) {
        Order order = orderRepository.findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(
                    ErrorCode.INVALID_ORDER_STATUS, ErrorCode.INVALID_ORDER_STATUS.defaultMessage(),
                    java.util.Map.of("currentStatus", order.getStatus().name()));
        }
        if (order.isHoldExpired(now)) {
            throw new BusinessException(ErrorCode.HOLD_EXPIRED);
        }
        return order;
    }

    private LocalDateTime earliestClosingAt(Long orderId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        return items.stream()
                .map(OrderItem::getProductClosingAt)
                .min(LocalDateTime::compareTo)
                .orElseThrow(() -> new IllegalStateException("주문 품목이 없습니다 — orderId=" + orderId));
    }

    public record AssignResult(Order order, SlotAssignment assignment, long holdRemainingSeconds) {
    }
}
