package kr.savepick.order.payment;

/**
 * 05-state-rules.md §4.1 — 결제 시도 상태 3개. {@code payment_attempts.status}와 문자 그대로 일치한다.
 */
public enum PaymentAttemptStatus {
    REQUESTED,
    SUCCEEDED,
    FAILED
}
