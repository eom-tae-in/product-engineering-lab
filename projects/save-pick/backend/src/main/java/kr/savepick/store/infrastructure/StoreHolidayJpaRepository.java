package kr.savepick.store.infrastructure;

import java.util.List;
import kr.savepick.store.domain.StoreHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * store_holidays는 항상 Store를 통해서만 다루는 하위 목록이라 도메인 리포지토리 인터페이스를 따로 두지 않는다
 * (account 슬라이스의 AuthSessionJpaRepository와 같은 패턴 — 14-project-structure.md §4).
 */
public interface StoreHolidayJpaRepository extends JpaRepository<StoreHoliday, Long> {

    List<StoreHoliday> findByStoreIdOrderByHolidayDateAsc(short storeId);

    void deleteByStoreId(short storeId);
}
