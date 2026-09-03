package kr.savepick.order.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import kr.savepick.order.domain.OrderItem;

/**
 * API-024·API-114 공용.
 *
 * <p>{@code productClosingAt}은 주문 시점에 스냅샷으로 저장해 둔 상품 마감 시각이다
 * ({@code order_items.product_closing_at}). 취소했을 때 재고가 되돌아오는지가 이 시각으로
 * 갈리므로(BR-019 — 마감 후 취소분은 복구하지 않고 폐기로 기록한다) 취소 화면(SC-011·SC-110)이
 * 실행 전에 안내하려면 필요하다.
 */
public record OrderDetailItemResponse(
        Long productId, String name, int quantity, int unitPrice, int lineAmount,
        OffsetDateTime productClosingAt) {

    public static OrderDetailItemResponse from(OrderItem item, ZoneId zone) {
        return new OrderDetailItemResponse(
                item.getProductId(), item.getProductName(), item.getQuantity(),
                item.getUnitPrice(), item.getLineAmount(),
                item.getProductClosingAt().atZone(zone).toOffsetDateTime());
    }
}
