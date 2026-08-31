package kr.savepick.order.infrastructure;

import java.util.List;
import kr.savepick.order.domain.OrderStatusHistory;
import kr.savepick.order.domain.OrderStatusHistoryRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryJpaRepository extends JpaRepository<OrderStatusHistory, Long>, OrderStatusHistoryRepository {

    @Override
    List<OrderStatusHistory> findByOrderId(Long orderId);
}
