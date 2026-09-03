package kr.savepick.product.batch;

import java.time.LocalDateTime;
import java.util.List;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.application.ProductClosingService;
import kr.savepick.product.domain.ProductRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * BATCH-02 상품 마감 상태 전환 — 30초 주기 (11-api-spec.md §11, BR-030, FR-034).
 * 대상 조회만 이 클래스가 하고, 전이 판정과 저장은 상품 1건씩
 * {@link ProductClosingService#closeIfDue}(상품 1건 = 트랜잭션 1개)에 위임한다
 * (14-project-structure.md §6.2). 고객 조회 API는 배치를 기다리지 않고 같은 도메인
 * 메서드로 즉시 전환하므로, 이 배치는 상태값을 실제로 맞추는 역할만 한다.
 *
 * <p>{@code test} 프로파일에서는 비활성화한다 — 실제 시각 기준 30초 주기가 마감 시각을 과거로
 * 조작해 두는 다른 통합 테스트와 경합하는 것을 막는다(order/batch/HoldExpiryReclaimJob과 같은
 * 패턴). 테스트는 {@link ProductClosingService}를 직접 호출해 검증한다.
 */
@Component
@Profile("!test")
public class ProductClosingJob {

    private static final Logger log = LoggerFactory.getLogger(ProductClosingJob.class);
    private static final int BATCH_SIZE = 200;

    private final ProductRepository productRepository;
    private final ProductClosingService productClosingService;
    private final ServerClock serverClock;

    public ProductClosingJob(
            ProductRepository productRepository, ProductClosingService productClosingService, ServerClock serverClock) {
        this.productRepository = productRepository;
        this.productClosingService = productClosingService;
        this.serverClock = serverClock;
    }

    @Scheduled(fixedDelayString = "${savepick.batch.product-closing.interval}")
    @SchedulerLock(name = "BATCH-02-product-closing", lockAtMostFor = "PT1M")
    public void run() {
        LocalDateTime now = serverClock.now();
        List<Long> productIds = productRepository.findIdsForClosing(now, BATCH_SIZE);
        int closed = 0;
        for (Long productId : productIds) {
            if (productClosingService.closeIfDue(productId, now)) {
                closed++;
            }
        }
        if (!productIds.isEmpty()) {
            log.info("BATCH-02 상품 마감 상태 전환 완료 — 대상 {}건, 처리 {}건", productIds.size(), closed);
        }
    }
}
