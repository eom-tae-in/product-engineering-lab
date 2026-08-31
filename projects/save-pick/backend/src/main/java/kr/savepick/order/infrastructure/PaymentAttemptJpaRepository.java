package kr.savepick.order.infrastructure;

import java.util.List;
import java.util.Optional;
import kr.savepick.order.payment.PaymentAttempt;
import kr.savepick.order.payment.PaymentAttemptRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptJpaRepository extends JpaRepository<PaymentAttempt, Long>, PaymentAttemptRepository {

    @Override
    Optional<PaymentAttempt> findByIdempotencyKey(String idempotencyKey);

    @Override
    List<PaymentAttempt> findByOrderIdOrderByAttemptNo(Long orderId);
}
