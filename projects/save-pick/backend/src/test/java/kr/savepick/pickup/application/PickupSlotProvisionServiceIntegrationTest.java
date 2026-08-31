package kr.savepick.pickup.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import kr.savepick.common.time.ServerClock;
import kr.savepick.pickup.domain.PickupSlot;
import kr.savepick.pickup.infrastructure.PickupSlotJpaRepository;
import kr.savepick.store.domain.Store;
import kr.savepick.store.domain.StoreHoliday;
import kr.savepick.store.infrastructure.StoreHolidayJpaRepository;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * BATCH-05 (11-api-spec.md §11, docs/16-test-plan.md TC-040·TC-104, BR-013·014·016).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class PickupSlotProvisionServiceIntegrationTest {

    @Autowired
    private PickupSlotProvisionService pickupSlotProvisionService;

    @Autowired
    private PickupSlotJpaRepository pickupSlotJpaRepository;

    @Autowired
    private StoreHolidayJpaRepository storeHolidayJpaRepository;

    @Autowired
    private ServerClock serverClock;

    @Test
    @DisplayName("TC_040_영업시간을_30분_단위_24개_시간대로_10시부터_22시까지_생성한다")
    void TC_040_영업시간을_30분_단위_24개_시간대로_10시부터_22시까지_생성한다() {
        LocalDate date = LocalDate.of(2026, 9, 10);

        int created = pickupSlotProvisionService.provisionForDate(date, serverClock.now());

        assertThat(created).isEqualTo(24);
        List<PickupSlot> slots = pickupSlotJpaRepository.findByStoreIdAndSlotDateOrderByStartAtAsc(Store.SINGLETON_ID, date);
        assertThat(slots).hasSize(24);
        assertThat(slots.get(0).getStartAt()).isEqualTo(LocalDateTime.of(date, LocalTime.of(10, 0)));
        assertThat(slots.get(0).getEndAt()).isEqualTo(LocalDateTime.of(date, LocalTime.of(10, 30)));
        assertThat(slots.get(slots.size() - 1).getStartAt()).isEqualTo(LocalDateTime.of(date, LocalTime.of(21, 30)));
        assertThat(slots.get(slots.size() - 1).getEndAt()).isEqualTo(LocalDateTime.of(date, LocalTime.of(22, 0)));
        assertThat(slots).allSatisfy(slot -> assertThat(slot.getCapacity()).isEqualTo((short) 20));
    }

    @Test
    @DisplayName("TC_104_같은_날짜에_두_번_실행해도_중복_슬롯이_생기지_않는다")
    void TC_104_같은_날짜에_두_번_실행해도_중복_슬롯이_생기지_않는다() {
        LocalDate date = LocalDate.of(2026, 9, 11);

        int firstRun = pickupSlotProvisionService.provisionForDate(date, serverClock.now());
        int secondRun = pickupSlotProvisionService.provisionForDate(date, serverClock.now());

        assertThat(firstRun).isEqualTo(24);
        assertThat(secondRun).isZero();
        List<PickupSlot> slots = pickupSlotJpaRepository.findByStoreIdAndSlotDateOrderByStartAtAsc(Store.SINGLETON_ID, date);
        assertThat(slots).hasSize(24);
    }

    @Test
    @DisplayName("휴무일에는_슬롯을_만들지_않는다")
    void 휴무일에는_슬롯을_만들지_않는다() {
        LocalDate date = LocalDate.of(2026, 9, 12);
        storeHolidayJpaRepository.save(StoreHoliday.of(Store.SINGLETON_ID, date));

        int created = pickupSlotProvisionService.provisionForDate(date, serverClock.now());

        assertThat(created).isZero();
        assertThat(pickupSlotJpaRepository.findByStoreIdAndSlotDateOrderByStartAtAsc(Store.SINGLETON_ID, date)).isEmpty();
    }
}
