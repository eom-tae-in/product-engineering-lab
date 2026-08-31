package kr.savepick.order.domain;

import java.util.List;

public interface OrderStatusHistoryRepository {

    OrderStatusHistory save(OrderStatusHistory history);

    List<OrderStatusHistory> findByOrderId(Long orderId);
}
