package kr.savepick.pickup.batch;

import kr.savepick.common.time.ServerClock;
import kr.savepick.pickup.application.PickupSlotProvisionService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * BATCH-05 픽업 시간대 사전 생성 — 매일 00:05 + 애플리케이션 기동 시 1회 (11-api-spec.md §11).
 * D+0, D+1 두 날짜에 대해 {@link PickupSlotProvisionService}를 날짜 단위로 호출한다
 * (14-project-structure.md §6.2 — 판정·계산은 하지 않는다).
 *
 * <p>{@code test} 프로파일에서는 비활성화한다 — 실제 시각 기준으로 슬롯을 생성해버리면 시간
 * 판정을 검증하는 다른 통합 테스트(임의의 startAt으로 직접 만드는 슬롯)와 {@code (store_id,
 * start_at)} 유니크 제약이 충돌할 수 있다. 테스트는 {@link PickupSlotProvisionService}를 직접
 * 호출해 검증한다 (docs/16-test-plan.md TC-104).
 */
@Component
@Profile("!test")
public class PickupSlotProvisionJob {

    private static final Logger log = LoggerFactory.getLogger(PickupSlotProvisionJob.class);

    private final PickupSlotProvisionService pickupSlotProvisionService;
    private final ServerClock serverClock;

    public PickupSlotProvisionJob(PickupSlotProvisionService pickupSlotProvisionService, ServerClock serverClock) {
        this.pickupSlotProvisionService = pickupSlotProvisionService;
        this.serverClock = serverClock;
    }

    @Scheduled(cron = "${savepick.batch.pickup-slot-provision.cron}", zone = "${savepick.time-zone}")
    @SchedulerLock(name = "BATCH-05-pickup-slot-provision-daily", lockAtMostFor = "PT5M")
    public void runDaily() {
        provision();
    }

    /** 애플리케이션 기동 시 1회(11-api-spec.md BATCH-05). */
    @EventListener(ApplicationReadyEvent.class)
    @SchedulerLock(name = "BATCH-05-pickup-slot-provision-startup", lockAtMostFor = "PT5M")
    public void runOnStartup() {
        provision();
    }

    private void provision() {
        var now = serverClock.now();
        java.time.LocalDate today = now.toLocalDate();
        int createdToday = pickupSlotProvisionService.provisionForDate(today, now);
        int createdTomorrow = pickupSlotProvisionService.provisionForDate(today.plusDays(1), now);
        log.info("BATCH-05 픽업 시간대 생성 완료 — today={}건, tomorrow={}건", createdToday, createdTomorrow);
    }
}
