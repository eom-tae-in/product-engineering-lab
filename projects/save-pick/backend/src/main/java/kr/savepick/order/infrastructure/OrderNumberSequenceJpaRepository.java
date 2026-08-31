package kr.savepick.order.infrastructure;

import jakarta.persistence.EntityManager;
import kr.savepick.order.domain.OrderNumberSequenceRepository;
import org.springframework.stereotype.Repository;

/**
 * {@code order_no_seq}(V3 마이그레이션)의 다음 값을 읽는다. PostgreSQL의 {@code nextval}은
 * 그 자체로 원자적이라 별도 락이 필요 없다.
 */
@Repository
public class OrderNumberSequenceJpaRepository implements OrderNumberSequenceRepository {

    private final EntityManager entityManager;

    public OrderNumberSequenceJpaRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public long nextValue() {
        Number value = (Number) entityManager.createNativeQuery("select nextval('order_no_seq')").getSingleResult();
        return value.longValue();
    }
}
