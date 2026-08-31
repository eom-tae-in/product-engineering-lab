package kr.savepick.order.domain;

/**
 * 05-state-rules.md §2.2 전이표의 "이벤트" 열. {@link OrderTransitionRule}이 이 이벤트와 현재
 * 상태 조합으로 다음 상태를 판정한다. 이번 슬라이스는 {@code HOLD_EXPIRE}·{@code ABANDON}만
 * 실제로 호출하지만(주문서 생성 1단계), 05 §2.2·2.3 전이표 전체를 완전하게 담는다.
 */
public enum OrderEvent {
    /** 결제 성공(고객) — PENDING → CONFIRMED. */
    PAYMENT_SUCCESS,
    /** 결제 1~2회 실패(고객) — PENDING 유지. */
    PAYMENT_FAIL_RETRY,
    /** 결제 3회째 실패(고객) — PENDING → FAILED. */
    PAYMENT_FAIL_FINAL,
    /** 선점 유효 시간 경과(시스템) — PENDING → EXPIRED. */
    HOLD_EXPIRE,
    /** 고객이 주문서 포기 — PENDING → EXPIRED. */
    ABANDON,
    /** 준비 완료 처리(관리자) — CONFIRMED → READY. */
    MARK_READY,
    /** 픽업 완료 처리(관리자) — CONFIRMED/READY → COMPLETED. */
    COMPLETE,
    /** 고객 취소 — CONFIRMED/READY → CANCELED. */
    CUSTOMER_CANCEL,
    /** 관리자 취소(사유 필수) — CONFIRMED/READY → CANCELED. */
    ADMIN_CANCEL,
    /** 픽업 종료 + 유예 30분 경과(시스템) — CONFIRMED/READY → NO_SHOW. */
    NO_SHOW
}
