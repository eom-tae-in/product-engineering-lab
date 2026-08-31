package kr.savepick.stock.domain;

/**
 * 05-state-rules.md §3.1 재고 선점 상태값. 문자 그대로 일치해야 한다 (14-project-structure.md §7.1).
 */
public enum HoldStatus {
    HELD,
    CONSUMED,
    RELEASED,
    EXPIRED
}
