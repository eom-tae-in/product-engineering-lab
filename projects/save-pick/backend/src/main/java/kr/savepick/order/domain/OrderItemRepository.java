package kr.savepick.order.domain;

import java.util.List;

public interface OrderItemRepository {

    OrderItem save(OrderItem item);

    List<OrderItem> findByOrderId(Long orderId);
}
