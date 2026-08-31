package kr.savepick.order.api;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import kr.savepick.account.infrastructure.AuthenticatedPrincipal;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.order.application.OrderCancelService;
import kr.savepick.order.application.OrderFulfillService;
import kr.savepick.order.application.OrderQueryService;
import kr.savepick.order.domain.OrderStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-112~117 관리자 주문 관리 (11-api-spec.md §9). 관리자 전용 — SecurityConfig가
 * {@code /api/admin/**}을 ROLE_ADMIN으로 막지만 컨트롤러에도 명시한다.
 */
@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminOrderController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final OrderQueryService orderQueryService;
    private final OrderFulfillService orderFulfillService;
    private final OrderCancelService orderCancelService;

    public AdminOrderController(
            OrderQueryService orderQueryService, OrderFulfillService orderFulfillService, OrderCancelService orderCancelService) {
        this.orderQueryService = orderQueryService;
        this.orderFulfillService = orderFulfillService;
        this.orderCancelService = orderCancelService;
    }

    /** API-112. FR-048, FR-053. */
    @GetMapping
    public AdminOrderListResponse listOrders(
            @RequestParam(required = false) String pickupDate,
            @RequestParam(required = false) Long slotId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<LocalDate> dates = pickupDate == null ? List.of() : List.of(parseDate(pickupDate));
        OrderStatus statusEnum = parseStatus(status);
        var result = orderQueryService.listForAdmin(dates, statusEnum, slotId, page, size);
        return AdminOrderListResponse.from(result, ZONE);
    }

    /** API-113. FR-049. */
    @GetMapping("/by-pickup-number")
    public AdminOrderDetailResponse getByPickupNumber(
            @RequestParam(required = false) String businessDate, @RequestParam int pickupNumber) {
        if (pickupNumber < 1 || pickupNumber > 999) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "pickupNumber는 1~999 범위여야 합니다.");
        }
        LocalDate date = businessDate == null ? null : parseDate(businessDate);
        var result = orderQueryService.getByPickupNumber(date, (short) pickupNumber);
        return AdminOrderDetailResponse.from(result, ZONE);
    }

    /** API-114. FR-050. */
    @GetMapping("/{orderId}")
    public AdminOrderDetailResponse getOrderDetail(@PathVariable Long orderId) {
        var result = orderQueryService.getDetailForAdmin(orderId);
        return AdminOrderDetailResponse.from(result, ZONE);
    }

    /** API-115. FR-051. */
    @PostMapping("/{orderId}/ready")
    public OrderFulfillResponse markReady(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long orderId) {
        var result = orderFulfillService.markReady(orderId, principal.memberId());
        return OrderFulfillResponse.from(result, ZONE);
    }

    /** API-116. FR-052. */
    @PostMapping("/{orderId}/complete")
    public OrderFulfillResponse complete(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long orderId) {
        var result = orderFulfillService.complete(orderId, principal.memberId());
        return OrderFulfillResponse.from(result, ZONE);
    }

    /** API-117. FR-054. */
    @PostMapping("/{orderId}/cancel")
    public OrderCancelResponse cancel(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable Long orderId,
            @RequestBody(required = false) AdminCancelOrderRequest request) {
        String reason = request == null ? null : request.reason();
        var result = orderCancelService.cancelByAdmin(orderId, principal.memberId(), reason);
        return OrderCancelResponse.from(result, ZONE);
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "날짜 형식이 올바르지 않습니다(YYYY-MM-DD).");
        }
    }

    private OrderStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "status 값이 올바르지 않습니다.");
        }
    }
}
