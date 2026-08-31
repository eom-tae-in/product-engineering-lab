package kr.savepick.order.domain;

/**
 * API-024 응답의 {@code cancelUnavailableReason} 값 집합(11-api-spec.md §5, FR-030).
 */
public enum CancelUnavailableReason {
    CANCEL_DEADLINE_PASSED,
    ALREADY_COMPLETED,
    ALREADY_CANCELED,
    NO_SHOW
}
