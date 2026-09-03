package kr.savepick.product.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductRepository;
import kr.savepick.product.domain.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * PS-U3(14-project-structure.md) — 조회 전용 쿼리는 Spring Data 파생 메서드로 우선 작성한다.
 * 고객 목록(API-010)은 할인율·잔여수량처럼 시각에 따라 계산되는 값으로 정렬·필터링해야 해서
 * DB 쿼리만으로 표현하기 어렵다 — ON_SALE 상품 전체를 읽어와 application 계층에서
 * 도메인 정책(DiscountRatePolicy)으로 계산한 뒤 정렬·필터·페이지를 적용한다.
 */
public interface ProductJpaRepository extends JpaRepository<Product, Long>, ProductRepository {

    List<Product> findByStatusAndNameContainingIgnoreCase(ProductStatus status, String keyword);

    List<Product> findByStatus(ProductStatus status);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    /**
     * BATCH-02 대상 조회 — 11-api-spec.md §11의 {@code status IN ('DRAFT','ON_SALE','HIDDEN')
     * AND closing_at <= now()}를 그대로 옮긴 것이다(= CLOSED가 아닌 전부).
     * 부분 인덱스 {@code IX_products_closing_at_open (closing_at) WHERE status <> 'CLOSED'}가
     * 대상 집합과 같은 범위를 덮는다.
     */
    @Query("select p.id from Product p "
            + "where p.status in (kr.savepick.product.domain.ProductStatus.DRAFT, "
            + "kr.savepick.product.domain.ProductStatus.ON_SALE, "
            + "kr.savepick.product.domain.ProductStatus.HIDDEN) "
            + "and p.closingAt <= :now order by p.closingAt asc")
    List<Long> findIdsForClosing(@Param("now") LocalDateTime now, Pageable limit);

    @Override
    default List<Long> findIdsForClosing(LocalDateTime now, int limit) {
        return findIdsForClosing(now, PageRequest.of(0, limit));
    }
}
