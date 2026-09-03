package kr.savepick.product.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);

    Optional<Product> findById(Long id);

    boolean existsById(Long id);

    /**
     * BATCH-02 — 마감 시각이 지났는데 아직 CLOSED가 아닌 상품의 ID (11-api-spec.md §11).
     * 전이 판정 자체는 {@link Product#closeIfDue}가 하고 이 쿼리는 후보만 좁힌다.
     */
    List<Long> findIdsForClosing(LocalDateTime now, int limit);
}
