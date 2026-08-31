package kr.savepick.store.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import kr.savepick.store.application.StoreQueryService.StoreSettings;
import kr.savepick.store.domain.Store;

/** API-120 (11-api-spec.md §10). */
public record StoreSettingsResponse(
        String name,
        String address,
        String phone,
        @JsonFormat(pattern = "HH:mm") LocalTime openTime,
        @JsonFormat(pattern = "HH:mm") LocalTime closeTime,
        int slotUnitMinutes,
        int defaultSlotCapacity,
        List<LocalDate> holidays) {

    public static StoreSettingsResponse from(StoreSettings settings) {
        Store store = settings.store();
        return new StoreSettingsResponse(
                store.getName(),
                store.getAddress(),
                store.getPhone(),
                store.getOpenTime(),
                store.getCloseTime(),
                store.getSlotUnitMinutes(),
                store.getDefaultSlotCapacity(),
                settings.holidays());
    }
}
