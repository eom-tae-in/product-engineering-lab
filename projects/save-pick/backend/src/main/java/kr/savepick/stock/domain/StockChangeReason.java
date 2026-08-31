package kr.savepick.stock.domain;

/**
 * 10-erd.md stock_ledgers.reason의 7가지 값. CHK_stock_ledgers_reason과 문자 그대로 일치한다.
 */
public enum StockChangeReason {
    ADMIN_ADJUST,
    HOLD,
    HOLD_RELEASE,
    HOLD_EXPIRE,
    CONFIRM,
    CANCEL_RESTORE,
    CANCEL_DISCARD
}
