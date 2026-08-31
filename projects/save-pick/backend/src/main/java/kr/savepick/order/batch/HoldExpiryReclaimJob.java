package kr.savepick.order.batch;

import java.time.LocalDateTime;
import java.util.List;
import kr.savepick.common.time.ServerClock;
import kr.savepick.order.application.OrderExpiryService;
import kr.savepick.order.domain.OrderRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * BATCH-01 선점 만료 회수 — 30초 주기 (11-api-spec.md §11, 13번 §2.3, BR-007·008).
 * 대상 조회(100건)만 이 클래스가 하고, 판정·재고 회수·이력 기록은 건별로
 * {@link OrderExpiryService#expirePendingOrderById}(주문 1건 = 트랜잭션 1개)에 위임한다
 * (14-project-structure.md §6.2). API-018·API-022의 "자기 주문 한정" 종결과 같은 서비스를
 * 공유한다(L1 — 규칙은 한 곳에만 둔다).
 *
 * <p>{@code test} 프로파일에서는 비활성화한다 — 실제 시각 기준 30초 주기가 다른 통합 테스트의
 * (테스트 전용으로 과거 시각을 흉내 낸) 주문·재고 상태와 경합하는 것을 막는다
 * (pickup/batch/PickupSlotProvisionJob과 같은 패턴). 테스트는 {@link OrderExpiryService}를
 * 직접 호출해 검증한다.
 */
@Component
@Profile("!test")
public class HoldExpiryReclaimJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryReclaimJob.class);
    private static final int BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderExpiryService orderExpiryService;
    private final ServerClock serverClock;

    public HoldExpiryReclaimJob(OrderRepository orderRepository, OrderExpiryService orderExpiryService, ServerClock serverClock) {
        this.orderRepository = orderRepository;
        this.orderExpiryService = orderExpiryService;
        this.serverClock = serverClock;
    }

    @Scheduled(fixedDelayString = "${savepick.batch.hold-expiry.interval}")
    @SchedulerLock(name = "BATCH-01-hold-expiry", lockAtMostFor = "PT1M")
    public void run() {
        LocalDateTime now = serverClock.now();
        List<Long> orderIds = orderRepository.findPendingIdsForHoldExpiry(now, BATCH_SIZE);
        int processed = 0;
        for (Long orderId : orderIds) {
            if (orderExpiryService.expirePendingOrderById(orderId, now)) {
                processed++;
            }
        }
        if (!orderIds.isEmpty()) {
            log.info("BATCH-01 선점 만료 회수 완료 — 대상 {}건, 처리 {}건", orderIds.size(), processed);
        }
    }
}
