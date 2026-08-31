package kr.savepick.account.domain;

/**
 * BR-023 노쇼 누적 제재 상태. members.order_permission과 1:1.
 */
public enum OrderPermission {
    ALLOWED,
    RESTRICTED
}
