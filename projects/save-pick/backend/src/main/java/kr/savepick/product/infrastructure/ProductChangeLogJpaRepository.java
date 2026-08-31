package kr.savepick.product.infrastructure;

import kr.savepick.product.domain.ProductChangeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductChangeLogJpaRepository extends JpaRepository<ProductChangeLog, Long> {

    Page<ProductChangeLog> findByProductIdOrderByChangedAtDesc(Long productId, Pageable pageable);
}
