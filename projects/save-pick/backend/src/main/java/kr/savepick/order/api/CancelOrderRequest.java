package kr.savepick.order.api;

/** API-025. {@code confirmed}가 true가 아니면 실행하지 않는다(FR-030, BR-024). */
public record CancelOrderRequest(Boolean confirmed) {
}
