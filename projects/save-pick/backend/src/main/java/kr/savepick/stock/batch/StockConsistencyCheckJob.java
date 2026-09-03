package kr.savepick.stock.batch;

import java.time.LocalDateTime;
import java.util.List;
import kr.savepick.common.time.ServerClock;
import kr.savepick.stock.application.StockConsistencyCheckService;
import kr.savepick.stock.application.StockConsistencyCheckService.ConsistencyCheckResult;
import kr.savepick.stock.infrastructure.ProductStockJpaRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * BATCH-04 재고 정합성 점검 — 매일 03:00 (11-api-spec.md §11, 13-inventory-concurrency.md §9.1,
 * FR-046, BR-006). 대상 조회와 결과 로깅만 이 클래스가 하고, 비교는 상품 1건씩
 * {@link StockConsistencyCheckService#check}에 위임한다(14-project-structure.md §6.2).
 *
 * <p><b>값을 보정하지 않는다.</b> 자동 보정은 불일치의 원인을 지워 버리므로 경보만 남기고,
 * 원장({@code stock_ledgers})으로 원인을 추적한 뒤 사람이 결정한다. 그래서 이 배치는 어떤
 * 쓰기도 하지 않는다.
 *
 * <p>{@code test} 프로파일에서는 비활성화한다(다른 배치와 같은 이유 — 실제 시각 기준 실행이
 * 통합 테스트와 경합하지 않게 한다). 테스트는 {@link StockConsistencyCheckService}를 직접
 * 호출해 검증한다 (docs/16-test-plan.md TC-086).
 */
@Component
@Profile("!test")
public class StockConsistencyCheckJob {

    private static final Logger log = LoggerFactory.getLogger(StockConsistencyCheckJob.class);

    private final ProductStockJpaRepository productStockJpaRepository;
    private final StockConsistencyCheckService stockConsistencyCheckService;
    private final ServerClock serverClock;

    public StockConsistencyCheckJob(
            ProductStockJpaRepository productStockJpaRepository,
            StockConsistencyCheckService stockConsistencyCheckService,
            ServerClock serverClock) {
        this.productStockJpaRepository = productStockJpaRepository;
        this.stockConsistencyCheckService = stockConsistencyCheckService;
        this.serverClock = serverClock;
    }

    @Scheduled(cron = "${savepick.batch.stock-consistency-check.cron}", zone = "${savepick.time-zone}")
    @SchedulerLock(name = "BATCH-04-stock-consistency-check", lockAtMostFor = "PT30M")
    public void run() {
        LocalDateTime now = serverClock.now();
        List<Long> productIds = productStockJpaRepository.findAllProductIds();

        int mismatched = 0;
        for (Long productId : productIds) {
            ConsistencyCheckResult result = stockConsistencyCheckService.check(productId, now).orElse(null);
            if (result == null || result.consistent()) {
                continue;
            }
            mismatched++;
            alert(result);
        }
        log.info("BATCH-04 재고 정합성 점검 완료 — 점검 {}건, 불일치 {}건", productIds.size(), mismatched);
    }

    /** 로그만 보고 판단할 수 있도록 상품 ID와 실제값·기대값을 항목별로 남긴다. */
    private void alert(ConsistencyCheckResult result) {
        log.error(
                "BATCH-04 재고 정합성 불일치 — productId={} "
                        + "held(실제={}, 기대={}, 일치={}), confirmed(실제={}, 기대={}, 일치={}), total(실제={}, 기대={}, 일치={}). "
                        + "값을 보정하지 않는다 — stock_ledgers에서 원인을 확인한 뒤 사람이 결정한다 (13번 §9.1)",
                result.productId(),
                result.actualHeldQuantity(), result.expectedHeldQuantity(), result.heldMatches(),
                result.actualConfirmedQuantity(), result.expectedConfirmedQuantity(), result.confirmedMatches(),
                result.actualTotalQuantity(), result.expectedTotalQuantity(), result.totalMatches());
    }
}
