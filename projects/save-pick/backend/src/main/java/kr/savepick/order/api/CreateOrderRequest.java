package kr.savepick.order.api;

import java.util.List;

/** API-017. 생략하면 장바구니 전 품목을 대상으로 한다. */
public record CreateOrderRequest(List<Long> cartItemIds) {
}
