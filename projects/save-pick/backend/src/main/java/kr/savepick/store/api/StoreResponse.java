package kr.savepick.store.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;
import kr.savepick.store.domain.Store;

/** API-009 (11-api-spec.md §1). */
public record StoreResponse(
        String name,
        String address,
        String phone,
        @JsonFormat(pattern = "HH:mm") LocalTime openTime,
        @JsonFormat(pattern = "HH:mm") LocalTime closeTime,
        int slotUnitMinutes) {

    public static StoreResponse from(Store store) {
        return new StoreResponse(
                store.getName(),
                store.getAddress(),
                store.getPhone(),
                store.getOpenTime(),
                store.getCloseTime(),
                store.getSlotUnitMinutes());
    }
}
