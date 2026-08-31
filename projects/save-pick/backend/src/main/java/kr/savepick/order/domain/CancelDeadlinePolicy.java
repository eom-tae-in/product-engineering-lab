package kr.savepick.order.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * BR-018 — 고객은 픽업 시간대 시작 1시간 전까지만 확정 주문을 직접 취소할 수 있다. 관리자
 * 취소는 이 판정을 건너뛴다(BR-020, {@code OrderCancelService}가 호출하지 않는다). 순수 정책이므로
 * {@code Clock}을 직접 호출하지 않고 {@code now}를 인자로 받는다(14-project-structure.md §9.1).
 */
public final class CancelDeadlinePolicy {

    private CancelDeadlinePolicy() {
    }

    /** 결제 확정 시점(API-022)에 계산해 {@code orders.cancelable_until}에 고정한다. */
    public static LocalDateTime cancelableUntil(LocalDateTime pickupStartAt, Duration customerCancelDeadline) {
        return pickupStartAt.minus(customerCancelDeadline);
    }

    public static boolean isCancelable(LocalDateTime now, LocalDateTime cancelableUntil) {
        return !now.isAfter(cancelableUntil);
    }
}
