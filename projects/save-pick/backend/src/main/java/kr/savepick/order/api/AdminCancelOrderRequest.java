package kr.savepick.order.api;

/** API-117. 사유 누락·공백은 CANCEL_REASON_REQUIRED다(BR-020). */
public record AdminCancelOrderRequest(String reason) {
}
