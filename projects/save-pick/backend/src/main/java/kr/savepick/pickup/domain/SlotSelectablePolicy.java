package kr.savepick.pickup.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * BR-013·015·016·017 — 픽업 날짜·시간대 선택 가능 여부 판정. 순수 정책이므로 {@code Clock}을 직접
 * 호출하지 않고 {@code now}를 인자로 받는다 (14-project-structure.md §9.1).
 *
 * <p>우선순위(여러 조건이 동시에 해당할 때 대표 사유): 관리자 차단(BLOCKED) &gt; 예약 마감
 * (RESERVATION_CLOSED) &gt; 정원 도달(SLOT_FULL) &gt; 상품 마감 이후 시작(AFTER_PRODUCT_CLOSING).
 * 문서에 명시된 우선순위가 없어 이 순서를 채택했다(가정).
 */
public final class SlotSelectablePolicy {

    private SlotSelectablePolicy() {
    }

    /** BR-013 — 오늘(D+0)부터 {@code selectableDays - 1}일 뒤까지만 선택 가능하다. */
    public static boolean isDateSelectable(LocalDate date, LocalDate today, int selectableDays) {
        long diff = ChronoUnit.DAYS.between(today, date);
        return diff >= 0 && diff < selectableDays;
    }

    /**
     * BR-015·016·017 — 개별 시간대의 선택 가능 여부. {@code earliestClosingAt}이 null이면
     * BR-017 판정을 건너뛴다(호출자가 주문 품목이 없는 맥락에서 호출하는 경우는 없다).
     */
    public static Evaluation evaluate(
            PickupSlot slot, LocalDateTime now, LocalDateTime earliestClosingAt, Duration reservationCloseBefore) {
        if (slot.isBlocked()) {
            return Evaluation.unselectable(UnselectableReason.BLOCKED);
        }
        LocalDateTime reservationDeadline = slot.getStartAt().minus(reservationCloseBefore);
        if (now.isAfter(reservationDeadline)) {
            return Evaluation.unselectable(UnselectableReason.RESERVATION_CLOSED);
        }
        if (slot.isFull()) {
            return Evaluation.unselectable(UnselectableReason.SLOT_FULL);
        }
        if (earliestClosingAt != null && slot.getStartAt().isAfter(earliestClosingAt)) {
            return Evaluation.unselectable(UnselectableReason.AFTER_PRODUCT_CLOSING);
        }
        return Evaluation.allow();
    }

    public record Evaluation(boolean selectable, UnselectableReason reason) {
        public static Evaluation allow() {
            return new Evaluation(true, null);
        }

        public static Evaluation unselectable(UnselectableReason reason) {
            return new Evaluation(false, reason);
        }
    }
}
