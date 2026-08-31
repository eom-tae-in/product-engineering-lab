package kr.savepick.store.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/** API-121 (11-api-spec.md §10). */
public record UpdateStoreSettingsResponse(
        @JsonFormat(pattern = "HH:mm") LocalTime openTime,
        @JsonFormat(pattern = "HH:mm") LocalTime closeTime,
        int defaultSlotCapacity,
        List<LocalDate> holidays,
        int excludedFutureSlotCount,
        int keptConfirmedOrderCount,
        OffsetDateTime appliedFrom) {
}
