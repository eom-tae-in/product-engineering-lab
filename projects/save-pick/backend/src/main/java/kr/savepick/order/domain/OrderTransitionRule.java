package kr.savepick.order.domain;

import java.util.Map;
import java.util.Optional;
import static kr.savepick.order.domain.OrderEvent.ABANDON;
import static kr.savepick.order.domain.OrderEvent.ADMIN_CANCEL;
import static kr.savepick.order.domain.OrderEvent.COMPLETE;
import static kr.savepick.order.domain.OrderEvent.CUSTOMER_CANCEL;
import static kr.savepick.order.domain.OrderEvent.HOLD_EXPIRE;
import static kr.savepick.order.domain.OrderEvent.MARK_READY;
import static kr.savepick.order.domain.OrderEvent.NO_SHOW;
import static kr.savepick.order.domain.OrderEvent.PAYMENT_FAIL_FINAL;
import static kr.savepick.order.domain.OrderEvent.PAYMENT_FAIL_RETRY;
import static kr.savepick.order.domain.OrderEvent.PAYMENT_SUCCESS;
import static kr.savepick.order.domain.OrderStatus.CANCELED;
import static kr.savepick.order.domain.OrderStatus.COMPLETED;
import static kr.savepick.order.domain.OrderStatus.CONFIRMED;
import static kr.savepick.order.domain.OrderStatus.EXPIRED;
import static kr.savepick.order.domain.OrderStatus.FAILED;
import static kr.savepick.order.domain.OrderStatus.PENDING;
import static kr.savepick.order.domain.OrderStatus.READY;

/**
 * 05-state-rules.md §2.2(허용 전이)·§2.3(허용하지 않는 전이 — 명시)을 그대로 코드로 옮긴다.
 * 순수 정책이므로 DB·시각을 참조하지 않는다(14-project-structure.md §3.2). 주문서 생성(없음 →
 * PENDING)은 이 표에 담지 않는다 — 그 판정은 {@code UQ_orders_member_pending}과
 * {@code OrderDraftService}의 몫이지 상태 "전이"가 아니다.
 *
 * <p>이번 슬라이스(order 1단계)는 {@link OrderEvent#HOLD_EXPIRE}·{@link OrderEvent#ABANDON}만
 * 실제로 호출한다. 나머지(결제·취소·노쇼 관련 이벤트)는 2단계에서 호출하지만, 전이 규칙 자체는
 * 지금 완전하게 만들어 둔다(과제 지시).
 */
public final class OrderTransitionRule {

    private static final Map<TransitionKey, OrderStatus> ALLOWED = Map.ofEntries(
            Map.entry(new TransitionKey(PENDING, PAYMENT_SUCCESS), CONFIRMED),
            Map.entry(new TransitionKey(PENDING, PAYMENT_FAIL_RETRY), PENDING),
            Map.entry(new TransitionKey(PENDING, PAYMENT_FAIL_FINAL), FAILED),
            Map.entry(new TransitionKey(PENDING, HOLD_EXPIRE), EXPIRED),
            Map.entry(new TransitionKey(PENDING, ABANDON), EXPIRED),
            Map.entry(new TransitionKey(CONFIRMED, MARK_READY), READY),
            Map.entry(new TransitionKey(CONFIRMED, COMPLETE), COMPLETED),
            Map.entry(new TransitionKey(CONFIRMED, CUSTOMER_CANCEL), CANCELED),
            Map.entry(new TransitionKey(CONFIRMED, ADMIN_CANCEL), CANCELED),
            Map.entry(new TransitionKey(CONFIRMED, NO_SHOW), OrderStatus.NO_SHOW),
            Map.entry(new TransitionKey(READY, COMPLETE), COMPLETED),
            Map.entry(new TransitionKey(READY, CUSTOMER_CANCEL), CANCELED),
            Map.entry(new TransitionKey(READY, ADMIN_CANCEL), CANCELED),
            Map.entry(new TransitionKey(READY, NO_SHOW), OrderStatus.NO_SHOW));

    private OrderTransitionRule() {
    }

    /** 허용되면 다음 상태를, 아니면(05 §2.3 포함 그 외 모든 조합) 빈 값을 돌려준다. */
    public static Optional<OrderStatus> next(OrderStatus from, OrderEvent event) {
        return Optional.ofNullable(ALLOWED.get(new TransitionKey(from, event)));
    }

    public static boolean isAllowed(OrderStatus from, OrderEvent event) {
        return ALLOWED.containsKey(new TransitionKey(from, event));
    }

    private record TransitionKey(OrderStatus from, OrderEvent event) {
    }
}
