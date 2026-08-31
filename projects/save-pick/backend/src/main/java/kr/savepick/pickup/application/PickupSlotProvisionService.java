package kr.savepick.pickup.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import kr.savepick.pickup.domain.PickupSlotRepository;
import kr.savepick.store.application.StoreQueryService;
import kr.savepick.store.domain.Store;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BATCH-05 픽업 시간대 사전 생성 (BR-013·014·016). 트리거({@code pickup/batch/PickupSlotProvisionJob})가
 * 이 서비스를 날짜 단위로 호출한다(14-project-structure.md §6.2 — 배치는 판정·계산을 하지 않는다).
 */
@Service
public class PickupSlotProvisionService {

    private final PickupSlotRepository pickupSlotRepository;
    private final StoreQueryService storeQueryService;

    public PickupSlotProvisionService(PickupSlotRepository pickupSlotRepository, StoreQueryService storeQueryService) {
        this.pickupSlotRepository = pickupSlotRepository;
        this.storeQueryService = storeQueryService;
    }

    /**
     * @return 새로 생성한 슬롯 수(멱등 — 이미 있으면 건너뛴다, TC-104). 휴무일이면 0을 돌려주고
     * 아무것도 만들지 않는다.
     */
    @Transactional
    public int provisionForDate(LocalDate date, LocalDateTime now) {
        StoreQueryService.StoreSettings settings = storeQueryService.getStoreSettings();
        if (settings.holidays().contains(date)) {
            return 0;
        }

        Store store = settings.store();
        LocalTime openTime = store.getOpenTime();
        LocalTime closeTime = store.getCloseTime();
        short slotUnitMinutes = store.getSlotUnitMinutes();
        short capacity = store.getDefaultSlotCapacity();

        int created = 0;
        LocalTime slotStart = openTime;
        while (slotStart.isBefore(closeTime)) {
            LocalTime slotEnd = slotStart.plusMinutes(slotUnitMinutes);
            LocalDateTime startAt = date.atTime(slotStart);
            LocalDateTime endAt = date.atTime(slotEnd);
            created += pickupSlotRepository.insertIfAbsent(Store.SINGLETON_ID, date, startAt, endAt, capacity, now);
            slotStart = slotEnd;
        }
        return created;
    }
}
