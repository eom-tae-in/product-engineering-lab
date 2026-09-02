package kr.savepick.store.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-121 (11-api-spec.md §10, BR-003, BR-014, BR-016). 테스트 메서드마다 트랜잭션을 롤백해
 * 단일 행뿐인 stores 테이블의 상태가 다른 테스트로 새어나가지 않게 한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class StoreSettingServiceIntegrationTest {

    @Autowired
    private StoreSettingService storeSettingService;

    @Autowired
    private StoreQueryService storeQueryService;

    @Autowired
    private ServerClock serverClock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 영향 범위 지표(excludedFutureSlotCount·keptConfirmedOrderCount)는 D+0·D+1의
     * {@code pickup_slots} **전체**를 세므로, 같은 JVM에서 먼저 끝난 트랜잭션 없는 테스트들이
     * 커밋해 둔 슬롯이 남아 있으면 개수가 실행 순서에 따라 달라진다.
     *
     * <p>참조 중인 슬롯을 남겨두면(FK 회피) 부족하다 — 취소된 주문이 참조하는 슬롯은
     * {@code reserved_count = 0}이라 "제외 대상"으로 세어지고, 확정 주문이 남은 슬롯은
     * "유지된 주문 수"에 더해진다. 그래서 참조를 먼저 끊고 두 날짜의 슬롯을 모두 지워
     * 두 테스트가 스스로 만든 슬롯만 세게 한다.
     *
     * <p>이 클래스는 {@code @Transactional}이라 이 정리도 테스트가 끝나면 롤백된다 —
     * 다른 테스트의 데이터를 실제로 지우지 않는다.
     */
    @BeforeEach
    void isolatePickupSlotsOfTodayAndTomorrow() {
        jdbcTemplate.update(
                "UPDATE orders SET pickup_slot_id = NULL WHERE pickup_slot_id IN "
                        + "(SELECT id FROM pickup_slots WHERE slot_date IN (CURRENT_DATE, CURRENT_DATE + 1))");
        jdbcTemplate.update("DELETE FROM pickup_slots WHERE slot_date IN (CURRENT_DATE, CURRENT_DATE + 1)");
    }

    @Test
    @DisplayName("TC-101_종료_시각이_시작_시각보다_같거나_이르면_BUSINESS_HOUR_INVALID이다")
    void TC_101_종료_시각이_시작_시각보다_같거나_이르면_BUSINESS_HOUR_INVALID이다() {
        assertThatThrownBy(() -> storeSettingService.updateSettings(
                        LocalTime.of(22, 0), LocalTime.of(10, 0), (short) 20, List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.BUSINESS_HOUR_INVALID);
    }

    @Test
    @DisplayName("30분_단위가_아니면_BUSINESS_HOUR_INVALID이다")
    void 삼십분_단위가_아니면_BUSINESS_HOUR_INVALID이다() {
        assertThatThrownBy(() -> storeSettingService.updateSettings(
                        LocalTime.of(10, 15), LocalTime.of(21, 0), (short) 20, List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.BUSINESS_HOUR_INVALID);
    }

    @Test
    @DisplayName("정상적인_설정_변경은_재조회하면_반영돼_있다")
    void 정상적인_설정_변경은_재조회하면_반영돼_있다() {
        storeSettingService.updateSettings(
                LocalTime.of(10, 0), LocalTime.of(21, 0), (short) 12, List.of(LocalDate.of(2026, 9, 1)));

        var settings = storeQueryService.getStoreSettings();

        assertThat(settings.store().getOpenTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(settings.store().getCloseTime()).isEqualTo(LocalTime.of(21, 0));
        assertThat(settings.store().getDefaultSlotCapacity()).isEqualTo((short) 12);
        assertThat(settings.holidays()).containsExactly(LocalDate.of(2026, 9, 1));
    }

    @Test
    @DisplayName("휴무일_목록은_전체_교체된다")
    void 휴무일_목록은_전체_교체된다() {
        storeSettingService.updateSettings(
                LocalTime.of(10, 0), LocalTime.of(22, 0), (short) 20,
                List.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5)));

        storeSettingService.updateSettings(
                LocalTime.of(10, 0), LocalTime.of(22, 0), (short) 20,
                List.of(LocalDate.of(2026, 10, 3)));

        var settings = storeQueryService.getStoreSettings();

        assertThat(settings.holidays()).containsExactly(LocalDate.of(2026, 10, 3));
    }

    @Test
    @DisplayName("픽업_슬롯이_없으면_excludedFutureSlotCount와_keptConfirmedOrderCount는_0이다")
    void 픽업_슬롯이_없으면_excludedFutureSlotCount와_keptConfirmedOrderCount는_0이다() {
        StoreSettingService.UpdateResult result = storeSettingService.updateSettings(
                LocalTime.of(10, 0), LocalTime.of(21, 0), (short) 20, List.of());

        assertThat(result.excludedFutureSlotCount()).isZero();
        assertThat(result.keptConfirmedOrderCount()).isZero();
        assertThat(result.appliedFrom()).isNotNull();
    }

    @Test
    @DisplayName("FR_056_영업_종료_시각을_앞당기면_예약_없는_미래_슬롯은_제외되고_확정_주문이_있는_슬롯은_유지된다")
    void FR_056_영업_종료_시각을_앞당기면_예약_없는_미래_슬롯은_제외되고_확정_주문이_있는_슬롯은_유지된다() {
        // BATCH-05는 D+0·D+1에만 슬롯을 만든다 — 여기서는 날짜·시간 경계 계산의 단순함을 위해
        // 내일(D+1) 15:00 이후 슬롯만 직접 만든다(store 도메인은 pickup 엔티티를 몰라도 되도록
        // StoreSettingImpactReadDao가 pickup_slots를 네이티브 쿼리로만 읽는다).
        LocalDate tomorrow = serverClock.now().toLocalDate().plusDays(1);
        insertSlot(tomorrow, LocalTime.of(15, 0), (short) 0);   // 예약 없음 — 제외 대상
        insertSlot(tomorrow, LocalTime.of(15, 30), (short) 3);  // 확정 주문 3건 유지 — 반영 안 함(reserved_count 그대로)
        insertSlot(tomorrow, LocalTime.of(9, 30), (short) 0);   // 새 마감 시각 이전 — 영향 없음

        StoreSettingService.UpdateResult result = storeSettingService.updateSettings(
                LocalTime.of(10, 0), LocalTime.of(15, 0), (short) 20, List.of());

        assertThat(result.excludedFutureSlotCount()).isEqualTo(1);
        assertThat(result.keptConfirmedOrderCount()).isEqualTo(3);
    }

    /**
     * {@link #isolatePickupSlotsOfTodayAndTomorrow()}가 두 날짜를 비워 두므로 통상 충돌은
     * 없지만, {@code UNIQUE(store_id, start_at)} 충돌이 나더라도 이 테스트가 원하는
     * reserved_count로 덮어쓰게 해 둔다.
     */
    private void insertSlot(LocalDate date, LocalTime time, short reservedCount) {
        LocalDateTime startAt = date.atTime(time);
        LocalDateTime endAt = startAt.plusMinutes(30);
        jdbcTemplate.update(
                "INSERT INTO pickup_slots (store_id, slot_date, start_at, end_at, capacity, reserved_count, blocked, created_at) "
                        + "VALUES (1, ?, ?, ?, 20, ?, false, ?) "
                        + "ON CONFLICT (store_id, start_at) DO UPDATE SET reserved_count = EXCLUDED.reserved_count",
                java.sql.Date.valueOf(date), Timestamp.valueOf(startAt), Timestamp.valueOf(endAt), reservedCount,
                Timestamp.valueOf(serverClock.now()));
    }
}
