package kr.savepick.product.application;

import java.time.LocalDateTime;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BATCH-02 상품 마감 상태 전환 (11-api-spec.md §11, BR-030). 상품 1건 = 트랜잭션 1개로
 * 처리한다(14-project-structure.md §6.2 — 여러 건을 한 트랜잭션에 묶지 않는다).
 *
 * <p>전이 판정은 이 서비스가 하지 않고 {@link Product#closeIfDue}가 한다 — 조회 경로
 * ({@code ProductQueryService})의 지연 전환과 배치가 같은 도메인 메서드 하나만 쓴다
 * (L1 — 마감 규칙을 두 벌로 만들지 않는다). 상태값을 실제로 맞추는 것 외에 다른 일은 하지
 * 않는다: 선점·확정 주문은 건드리지 않고(BR-030), {@code product_change_logs}에도 남기지
 * 않는다 — 자동 전환에는 행위자(actor_id NOT NULL)가 없기 때문이다
 * (docs/16-test-plan.md TC-124, 12-auth.md AU-4).
 */
@Service
public class ProductClosingService {

    private final ProductRepository productRepository;

    public ProductClosingService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * @return 이 호출이 실제로 CLOSED로 전환했으면 true. 이미 CLOSED이거나 아직 마감 시각이
     * 지나지 않았으면(다른 경로가 먼저 처리했거나 관리자가 마감 시각을 미뤘으면) false.
     */
    @Transactional
    public boolean closeIfDue(Long productId, LocalDateTime now) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return false;
        }
        if (!product.closeIfDue(now)) {
            return false;
        }
        productRepository.save(product);
        return true;
    }
}
