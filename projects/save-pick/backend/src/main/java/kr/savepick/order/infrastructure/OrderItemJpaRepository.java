package kr.savepick.order.infrastructure;

import java.util.List;
import kr.savepick.order.domain.OrderItem;
import kr.savepick.order.domain.OrderItemRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemJpaRepository extends JpaRepository<OrderItem, Long>, OrderItemRepository {

    @Override
    List<OrderItem> findByOrderId(Long orderId);
}
