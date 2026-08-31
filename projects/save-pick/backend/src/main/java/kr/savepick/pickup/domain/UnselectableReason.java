package kr.savepick.pickup.domain;

/**
 * 11-api-spec.md API-020 — 픽업 시간대(또는 날짜)를 선택할 수 없는 사유.
 * 응답 필드 {@code unselectableReason}의 값과 문자 그대로 일치한다 (14-project-structure.md §7.1).
 */
public enum UnselectableReason {
    RESERVATION_CLOSED,
    SLOT_FULL,
    AFTER_PRODUCT_CLOSING,
    BLOCKED,
    HOLIDAY
}
