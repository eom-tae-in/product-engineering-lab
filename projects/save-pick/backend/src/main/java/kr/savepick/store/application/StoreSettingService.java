package kr.savepick.store.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.store.domain.BusinessHours;
import kr.savepick.store.domain.Store;
import kr.savepick.store.domain.StoreHoliday;
import kr.savepick.store.domain.StoreRepository;
import kr.savepick.store.infrastructure.StoreHolidayJpaRepository;
import kr.savepick.store.infrastructure.StoreSettingImpactReadDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-121 매장 운영 설정 변경 (BR-003, BR-014, BR-016).
 */
@Service
public class StoreSettingService {

    private final StoreRepository storeRepository;
    private final StoreHolidayJpaRepository storeHolidayJpaRepository;
    private final StoreSettingImpactReadDao storeSettingImpactReadDao;
    private final ServerClock serverClock;

    public StoreSettingService(
            StoreRepository storeRepository,
            StoreHolidayJpaRepository storeHolidayJpaRepository,
            StoreSettingImpactReadDao storeSettingImpactReadDao,
            ServerClock serverClock) {
        this.storeRepository = storeRepository;
        this.storeHolidayJpaRepository = storeHolidayJpaRepository;
        this.storeSettingImpactReadDao = storeSettingImpactReadDao;
        this.serverClock = serverClock;
    }

    /** API-121. openTime/closeTime/defaultSlotCapacity/holidays를 한 번에 갱신한다. */
    @Transactional
    public UpdateResult updateSettings(
            LocalTime openTime, LocalTime closeTime, short defaultSlotCapacity, List<LocalDate> holidays) {
        if (!BusinessHours.isValid(openTime, closeTime)) {
            throw new BusinessException(ErrorCode.BUSINESS_HOUR_INVALID);
        }
        BusinessHours businessHours = new BusinessHours(openTime, closeTime);

        Store store = storeRepository.findById(Store.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "stores 테이블에 초기 행이 없습니다 — V2__seed_store.sql 마이그레이션을 확인하세요."));

        LocalDateTime now = serverClock.now();
        store.updateOperatingSettings(businessHours, defaultSlotCapacity, now);
        store = storeRepository.save(store);

        List<LocalDate> appliedHolidays = holidays.stream().distinct().sorted().toList();
        replaceHolidays(appliedHolidays);

        // FR-056 — 영업 종료 시각을 앞당기면 그 이후 시작하고 아직 확정 주문이 없는 미래 슬롯만
        // 신규 선택에서 제외되고, 이미 확정 주문이 있는 슬롯은 그대로 유지된다. BATCH-05는 D+0·D+1
        // 두 날짜에만 슬롯을 만들어 두므로(pickup/batch/PickupSlotProvisionJob) 그 두 날짜만 본다.
        LocalDate today = now.toLocalDate();
        LocalDate tomorrow = today.plusDays(1);
        StoreSettingImpactReadDao.Impact impact = storeSettingImpactReadDao.measure(
                now, today, today.atTime(closeTime), tomorrow, tomorrow.atTime(closeTime));

        return new UpdateResult(store, appliedHolidays, impact.excludedFutureSlotCount(), impact.keptConfirmedOrderCount(), now);
    }

    /**
     * holidays 배열은 전체 교체로 처리한다 — 기존 store_holidays를 지우고 새로 받은 목록으로 다시 넣는다.
     */
    private void replaceHolidays(List<LocalDate> holidays) {
        storeHolidayJpaRepository.deleteByStoreId(Store.SINGLETON_ID);
        storeHolidayJpaRepository.flush();
        List<StoreHoliday> entities = holidays.stream()
                .map(date -> StoreHoliday.of(Store.SINGLETON_ID, date))
                .toList();
        storeHolidayJpaRepository.saveAll(entities);
    }

    public record UpdateResult(
            Store store,
            List<LocalDate> holidays,
            int excludedFutureSlotCount,
            int keptConfirmedOrderCount,
            LocalDateTime appliedFrom) {
    }
}
