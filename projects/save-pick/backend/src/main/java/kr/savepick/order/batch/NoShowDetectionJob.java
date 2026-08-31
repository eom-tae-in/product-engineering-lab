package kr.savepick.order.batch;

import java.time.LocalDateTime;
import java.util.List;
import kr.savepick.common.time.ServerClock;
import kr.savepick.order.application.NoShowService;
import kr.savepick.order.domain.OrderRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * BATCH-03 노쇼 자동 전환·누적 제재 — 3분 주기 (11-api-spec.md §11, BR-021~023).
 * 대상 조회만 이 클래스가 하고, 전환·정원 반납·제재 판정은 건별로
 * {@link NoShowService#convertToNoShow}(주문 1건 = 트랜잭션 1개)에 위임한다.
 *
 * <p>{@code test} 프로파일에서는 비활성화한다(HoldExpiryReclaimJob과 같은 이유). 테스트는
 * {@link NoShowService}를 직접 호출해 검증한다.
 */
@Component
@Profile("!test")
public class NoShowDetectionJob {

    private static final Logger log = LoggerFactory.getLogger(NoShowDetectionJob.class);
    private static final int BATCH_SIZE = 200;

    private final OrderRepository orderRepository;
    private final NoShowService noShowService;
    private final ServerClock serverClock;

    public NoShowDetectionJob(OrderRepository orderRepository, NoShowService noShowService, ServerClock serverClock) {
        this.orderRepository = orderRepository;
        this.noShowService = noShowService;
        this.serverClock = serverClock;
    }

    @Scheduled(fixedDelayString = "${savepick.batch.no-show.interval}")
    @SchedulerLock(name = "BATCH-03-no-show-detection", lockAtMostFor = "PT2M")
    public void run() {
        LocalDateTime now = serverClock.now();
        List<Long> orderIds = orderRepository.findConfirmedOrReadyIdsForNoShow(now, BATCH_SIZE);
        int processed = 0;
        for (Long orderId : orderIds) {
            if (noShowService.convertToNoShow(orderId, now)) {
                processed++;
            }
        }
        if (!orderIds.isEmpty()) {
            log.info("BATCH-03 노쇼 자동 전환 완료 — 대상 {}건, 처리 {}건", orderIds.size(), processed);
        }
    }
}
