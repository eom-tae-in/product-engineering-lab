package kr.savepick.store.application;

import java.time.LocalDate;
import java.util.List;
import kr.savepick.store.domain.Store;
import kr.savepick.store.domain.StoreHoliday;
import kr.savepick.store.domain.StoreRepository;
import kr.savepick.store.infrastructure.StoreHolidayJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-009 매장 정보 조회, API-120 매장 운영 설정 조회. 조회 전용이라 상태를 바꾸지 않는다
 * (14-project-structure.md §3.2).
 */
@Service
public class StoreQueryService {

    private final StoreRepository storeRepository;
    private final StoreHolidayJpaRepository storeHolidayJpaRepository;

    public StoreQueryService(StoreRepository storeRepository, StoreHolidayJpaRepository storeHolidayJpaRepository) {
        this.storeRepository = storeRepository;
        this.storeHolidayJpaRepository = storeHolidayJpaRepository;
    }

    /** API-009 (11-api-spec.md §1). 비로그인 포함 전체 공개. */
    @Transactional(readOnly = true)
    public Store getStore() {
        return findStore();
    }

    /** API-120 (11-api-spec.md §10). 관리자 전용 — 휴무일 목록까지 포함한다. */
    @Transactional(readOnly = true)
    public StoreSettings getStoreSettings() {
        Store store = findStore();
        List<LocalDate> holidays = storeHolidayJpaRepository
                .findByStoreIdOrderByHolidayDateAsc(Store.SINGLETON_ID)
                .stream()
                .map(StoreHoliday::getHolidayDate)
                .toList();
        return new StoreSettings(store, holidays);
    }

    private Store findStore() {
        return storeRepository.findById(Store.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "stores 테이블에 초기 행이 없습니다 — V2__seed_store.sql 마이그레이션을 확인하세요."));
    }

    public record StoreSettings(Store store, List<LocalDate> holidays) {
    }
}
