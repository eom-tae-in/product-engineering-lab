package kr.savepick.order.api;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
import kr.savepick.account.infrastructure.AuthenticatedPrincipal;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.order.application.OrderAbandonService;
import kr.savepick.order.application.OrderCancelService;
import kr.savepick.order.application.OrderDraftService;
import kr.savepick.order.application.OrderHoldQueryService;
import kr.savepick.order.application.OrderQueryService;
import kr.savepick.order.application.PickupSlotAssignService;
import kr.savepick.order.payment.PaymentAttemptService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-017~025 고객 주문 전체(11-api-spec.md §4·5). 인증 필요(고객) — 관리자 토큰은 차단한다
 * (TC-112, 14-project-structure.md §9.4). 소유권 검사는 각 애플리케이션 서비스가 조회 조건에
 * 넣는다(아니면 404).
 */
@RestController
@RequestMapping("/api/orders")
@PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
public class OrderController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final OrderDraftService orderDraftService;
    private final OrderHoldQueryService orderHoldQueryService;
    private final OrderAbandonService orderAbandonService;
    private final PickupSlotAssignService pickupSlotAssignService;
    private final PaymentAttemptService paymentAttemptService;
    private final OrderQueryService orderQueryService;
    private final OrderCancelService orderCancelService;
    private final int paymentMaxAttempts;

    public OrderController(
            OrderDraftService orderDraftService,
            OrderHoldQueryService orderHoldQueryService,
            OrderAbandonService orderAbandonService,
            PickupSlotAssignService pickupSlotAssignService,
            PaymentAttemptService paymentAttemptService,
            OrderQueryService orderQueryService,
            OrderCancelService orderCancelService,
            @Value("${savepick.payment.max-attempts}") int paymentMaxAttempts) {
        this.orderDraftService = orderDraftService;
        this.orderHoldQueryService = orderHoldQueryService;
        this.orderAbandonService = orderAbandonService;
        this.pickupSlotAssignService = pickupSlotAssignService;
        this.paymentAttemptService = paymentAttemptService;
        this.orderQueryService = orderQueryService;
        this.orderCancelService = orderCancelService;
        this.paymentMaxAttempts = paymentMaxAttempts;
    }

    /** API-017. FR-019, FR-021, FR-032, FR-034. */
    @PostMapping
    public ResponseEntity<OrderDraftResponse> createOrder(
            @AuthenticationPrincipal AuthenticatedPrincipal principal, @RequestBody(required = false) CreateOrderRequest request) {
        var cartItemIds = request == null ? null : request.cartItemIds();
        OrderDraftService.DraftResult result = orderDraftService.createDraft(principal.memberId(), cartItemIds);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderDraftResponse.of(result, paymentMaxAttempts, ZONE));
    }

    /** API-018. FR-020, FR-005. */
    @GetMapping("/{orderId}/hold")
    public HoldStatusResponse getHoldStatus(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long orderId) {
        OrderHoldQueryService.HoldStatusResult result = orderHoldQueryService.getHoldStatus(orderId, principal.memberId());
        return HoldStatusResponse.from(result, ZONE);
    }

    /** API-019. FR-020. */
    @DeleteMapping("/{orderId}")
    public AbandonOrderResponse abandonOrder(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long orderId) {
        OrderAbandonService.AbandonResult result = orderAbandonService.abandon(orderId, principal.memberId());
        return AbandonOrderResponse.from(result, ZONE);
    }

    /** API-020. FR-022, FR-023. */
    @GetMapping("/{orderId}/pickup-slots")
    public SelectableSlotsResponse getSelectableSlots(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable Long orderId,
            @RequestParam(required = false) LocalDate date) {
        var result = pickupSlotAssignService.getSelectableSlots(orderId, principal.memberId(), date);
        return SelectableSlotsResponse.from(result, ZONE);
    }

    /** API-021. FR-022, FR-023. */
    @PatchMapping("/{orderId}/pickup-slot")
    public AssignPickupSlotResponse assignPickupSlot(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable Long orderId,
            @Valid @RequestBody AssignPickupSlotRequest request) {
        var result = pickupSlotAssignService.assign(orderId, principal.memberId(), request.slotId());
        return AssignPickupSlotResponse.from(result, ZONE);
    }

    /** API-022. FR-021, FR-024, FR-025, FR-026. */
    @PostMapping("/{orderId}/payments")
    public PaymentResponse pay(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable Long orderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) PaymentRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key 헤더가 필요합니다.");
        }
        if (request == null || request.amount() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "amount는 필수입니다.");
        }
        var result = paymentAttemptService.pay(orderId, principal.memberId(), request.amount(), idempotencyKey);
        return PaymentResponse.from(result, ZONE);
    }

    /** API-023. FR-027. */
    @GetMapping
    public OrderListResponse listOrders(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean includeExpired,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = orderQueryService.listForCustomer(principal.memberId(), status, includeExpired, page, size);
        return OrderListResponse.from(result, ZONE);
    }

    /** API-024. FR-028, FR-030, FR-031, FR-033. */
    @GetMapping("/{orderId}")
    public OrderDetailResponse getOrderDetail(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long orderId) {
        var result = orderQueryService.getDetailForCustomer(orderId, principal.memberId());
        return OrderDetailResponse.from(result, ZONE);
    }

    /** API-025. FR-029, FR-030. */
    @PostMapping("/{orderId}/cancel")
    public OrderCancelResponse cancelOrder(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable Long orderId,
            @RequestBody(required = false) CancelOrderRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.confirmed())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "confirmed가 true여야 취소가 실행됩니다.");
        }
        var result = orderCancelService.cancelByCustomer(orderId, principal.memberId());
        return OrderCancelResponse.from(result, ZONE);
    }
}
