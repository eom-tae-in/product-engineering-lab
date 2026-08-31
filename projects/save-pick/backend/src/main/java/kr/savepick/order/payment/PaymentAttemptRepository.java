package kr.savepick.order.payment;

import java.util.List;
import java.util.Optional;

public interface PaymentAttemptRepository {

    PaymentAttempt save(PaymentAttempt attempt);

    /** {@code UNIQUE (idempotency_key)} — 같은 키 재전송을 식별한다(13번 §4). */
    Optional<PaymentAttempt> findByIdempotencyKey(String idempotencyKey);

    List<PaymentAttempt> findByOrderIdOrderByAttemptNo(Long orderId);
}
