package kr.savepick.order.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.order.domain.CancelDeadlinePolicy;
import kr.savepick.order.domain.CancelUnavailableReason;
import kr.savepick.order.domain.Order;
import kr.savepick.order.domain.OrderItem;
import kr.savepick.order.domain.OrderItemRepository;
import kr.savepick.order.domain.OrderRepository;
import kr.savepick.order.domain.OrderStatus;
import kr.savepick.order.domain.OrderStatusHistory;
import kr.savepick.order.domain.OrderStatusHistoryRepository;
import kr.savepick.order.payment.PaymentAttempt;
import kr.savepick.order.payment.PaymentAttemptRepository;
import kr.savepick.pickup.domain.PickupSlot;
import kr.savepick.pickup.domain.PickupSlotRepository;
import kr.savepick.store.domain.Store;
import kr.savepick.store.domain.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-023·024(고객), API-112~114(관리자) 주문 조회 (11-api-spec.md §5·9). 목록 규모가 크지
 * 않은 MVP 범위를 전제로 애플리케이션 계층에서 슬롯·품목을 함께 조립한다(PS-U3 임시 채택,
 * stock의 {@code StockQueryService}와 같은 패턴).
 */
@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PickupSlotRepository pickupSlotRepository;
    private final StoreRepository storeRepository;
    private final ServerClock serverClock;

    public OrderQueryService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            PickupSlotRepository pickupSlotRepository,
            StoreRepository storeRepository,
            ServerClock serverClock) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.pickupSlotRepository = pickupSlotRepository;
        this.storeRepository = storeRepository;
        this.serverClock = serverClock;
    }

    /** API-023. {@code statusGroup}은 IN_PROGRESS/COMPLETED/CANCELED/NO_SHOW 중 하나이거나 null이다. */
    @Transactional(readOnly = true)
    public CustomerOrderListPage listForCustomer(Long memberId, String statusGroup, boolean includeExpired, int page, int size) {
        List<OrderStatus> statuses = resolveCustomerStatuses(statusGroup, includeExpired);
        OrderRepository.OrderPage result = orderRepository.findByMemberIdAndStatusIn(memberId, statuses, page, size);

        List<CustomerOrderListItem> items = new ArrayList<>();
        for (Order order : result.items()) {
            PickupSlot slot = resolveSlot(order.getPickupSlotId());
            items.add(new CustomerOrderListItem(
                    order.getId(), order.getOrderNo(), order.getCreatedAt(), order.getStatus(),
                    slot == null ? null : slot.getStartAt(), slot == null ? null : slot.getEndAt(),
                    order.getPickupNumber(), order.getTotalAmount(), itemSummary(order.getId())));
        }
        return new CustomerOrderListPage(items, page, size, result.totalElements());
    }

    /** API-024. 본인 주문이 아니면 404(존재 여부를 알리지 않는다, FR-028 예외). */
    @Transactional(readOnly = true)
    public CustomerOrderDetail getDetailForCustomer(Long orderId, Long memberId) {
        Order order = orderRepository.findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return buildCustomerDetail(order);
    }

    /** API-112. {@code pickupDates}가 비어 있으면 오늘·내일로 대체한다. {@code status}가 null이면 PENDING·EXPIRED를 제외한다. */
    @Transactional(readOnly = true)
    public AdminOrderListPage listForAdmin(List<LocalDate> pickupDates, OrderStatus status, Long slotId, int page, int size) {
        LocalDateTime now = serverClock.now();
        List<LocalDate> dates = (pickupDates == null || pickupDates.isEmpty())
                ? List.of(now.toLocalDate(), now.toLocalDate().plusDays(1))
                : pickupDates;
        List<OrderStatus> statuses = status != null
                ? List.of(status)
                : List.of(OrderStatus.CONFIRMED, OrderStatus.READY, OrderStatus.COMPLETED,
                        OrderStatus.CANCELED, OrderStatus.NO_SHOW, OrderStatus.FAILED);

        OrderRepository.OrderPage result = orderRepository.findForAdmin(dates, statuses, slotId, page, size);
        List<AdminOrderListItem> items = new ArrayList<>();
        for (Order order : result.items()) {
            PickupSlot slot = resolveSlot(order.getPickupSlotId());
            items.add(new AdminOrderListItem(
                    order.getId(), order.getOrderNo(), order.getPickupNumber(), order.getContactName(), order.getStatus(),
                    slot == null ? null : slot.getStartAt(), slot == null ? null : slot.getEndAt(), order.getNoShowDueAt(),
                    order.getTotalAmount(), orderItemRepository.findByOrderId(order.getId()).size()));
        }
        return new AdminOrderListPage(items, page, size, result.totalElements());
    }

    /** API-114. */
    @Transactional(readOnly = true)
    public AdminOrderDetail getDetailForAdmin(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return buildAdminDetail(order);
    }

    /** API-113. {@code businessDate}가 null이면 오늘 영업일로 조회한다. */
    @Transactional(readOnly = true)
    public AdminOrderDetail getByPickupNumber(LocalDate businessDate, short pickupNumber) {
        LocalDate date = businessDate != null ? businessDate : serverClock.now().toLocalDate();
        Order order = orderRepository.findByPickupBusinessDateAndPickupNumber(date, pickupNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return buildAdminDetail(order);
    }

    private CustomerOrderDetail buildCustomerDetail(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        PickupSlot slot = resolveSlot(order.getPickupSlotId());
        List<OrderStatusHistory> history = orderStatusHistoryRepository.findByOrderId(order.getId());
        Store store = storeRepository.findById(Store.SINGLETON_ID).orElse(null);

        boolean cancelable = false;
        CancelUnavailableReason reason = null;
        LocalDateTime now = serverClock.now();
        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.READY) {
            if (order.getCancelableUntil() != null && CancelDeadlinePolicy.isCancelable(now, order.getCancelableUntil())) {
                cancelable = true;
            } else {
                reason = CancelUnavailableReason.CANCEL_DEADLINE_PASSED;
            }
        } else if (order.getStatus() == OrderStatus.COMPLETED) {
            reason = CancelUnavailableReason.ALREADY_COMPLETED;
        } else if (order.getStatus() == OrderStatus.CANCELED) {
            reason = CancelUnavailableReason.ALREADY_CANCELED;
        } else if (order.getStatus() == OrderStatus.NO_SHOW) {
            reason = CancelUnavailableReason.NO_SHOW;
        }

        return new CustomerOrderDetail(
                order, items, slot == null ? null : slot.getStartAt(), slot == null ? null : slot.getEndAt(),
                cancelable, reason, history, store, now);
    }

    private AdminOrderDetail buildAdminDetail(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        PickupSlot slot = resolveSlot(order.getPickupSlotId());
        List<OrderStatusHistory> history = orderStatusHistoryRepository.findByOrderId(order.getId());
        List<PaymentAttempt> attempts = paymentAttemptRepository.findByOrderIdOrderByAttemptNo(order.getId());

        List<String> availableActions = new ArrayList<>();
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            availableActions.add("READY");
            availableActions.add("COMPLETE");
            availableActions.add("CANCEL");
        } else if (order.getStatus() == OrderStatus.READY) {
            availableActions.add("COMPLETE");
            availableActions.add("CANCEL");
        }

        return new AdminOrderDetail(
                order, items, slot == null ? null : slot.getStartAt(), slot == null ? null : slot.getEndAt(),
                attempts, history, availableActions);
    }

    private PickupSlot resolveSlot(Long slotId) {
        return slotId == null ? null : pickupSlotRepository.findById(slotId).orElse(null);
    }

    private String itemSummary(Long orderId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        if (items.isEmpty()) {
            return "";
        }
        String first = items.get(0).getProductName();
        return items.size() == 1 ? first : first + " 외 " + (items.size() - 1) + "건";
    }

    private List<OrderStatus> resolveCustomerStatuses(String statusGroup, boolean includeExpired) {
        if (statusGroup == null) {
            List<OrderStatus> base = new ArrayList<>(List.of(
                    OrderStatus.CONFIRMED, OrderStatus.READY, OrderStatus.COMPLETED, OrderStatus.CANCELED, OrderStatus.NO_SHOW));
            if (includeExpired) {
                base.add(OrderStatus.EXPIRED);
                base.add(OrderStatus.FAILED);
            }
            return base;
        }
        return switch (statusGroup) {
            case "IN_PROGRESS" -> List.of(OrderStatus.CONFIRMED, OrderStatus.READY);
            case "COMPLETED" -> List.of(OrderStatus.COMPLETED);
            case "CANCELED" -> List.of(OrderStatus.CANCELED);
            case "NO_SHOW" -> List.of(OrderStatus.NO_SHOW);
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "status 값이 올바르지 않습니다.");
        };
    }

    public record CustomerOrderListItem(
            Long orderId, String orderNo, LocalDateTime orderedAt, OrderStatus status,
            LocalDateTime pickupStartAt, LocalDateTime pickupEndAt, Short pickupNumber, int totalAmount, String itemSummary) {
    }

    public record CustomerOrderListPage(List<CustomerOrderListItem> items, int number, int size, long totalElements) {
    }

    public record CustomerOrderDetail(
            Order order, List<OrderItem> items, LocalDateTime pickupStartAt, LocalDateTime pickupEndAt,
            boolean cancelable, CancelUnavailableReason cancelUnavailableReason, List<OrderStatusHistory> history,
            Store store, LocalDateTime serverTime) {
    }

    public record AdminOrderListItem(
            Long orderId, String orderNo, Short pickupNumber, String customerName, OrderStatus status,
            LocalDateTime pickupStartAt, LocalDateTime pickupEndAt, LocalDateTime noShowDueAt, int totalAmount, int itemCount) {
    }

    public record AdminOrderListPage(List<AdminOrderListItem> items, int number, int size, long totalElements) {
    }

    public record AdminOrderDetail(
            Order order, List<OrderItem> items, LocalDateTime pickupStartAt, LocalDateTime pickupEndAt,
            List<PaymentAttempt> paymentAttempts, List<OrderStatusHistory> history, List<String> availableActions) {
    }
}
