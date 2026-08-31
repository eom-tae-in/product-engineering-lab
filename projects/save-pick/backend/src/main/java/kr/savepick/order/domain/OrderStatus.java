package kr.savepick.order.domain;

/**
 * 05-state-rules.md §2.1 — 주문 상태 8개. 문자 그대로 orders.status 값과 일치한다.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    READY,
    COMPLETED,
    CANCELED,
    NO_SHOW,
    EXPIRED,
    FAILED
}
