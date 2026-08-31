package kr.savepick.order.api;

/** API-114. 픽업 응대 목적으로만 반환한다(FR-050). */
public record AdminOrderCustomerResponse(String name, String phone) {
}
