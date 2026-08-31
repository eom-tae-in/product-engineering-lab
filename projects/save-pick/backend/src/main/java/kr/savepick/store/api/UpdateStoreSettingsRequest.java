package kr.savepick.store.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * API-121 (11-api-spec.md §10). "종료 ≤ 시작"·"30분 단위 아님"은 형식 문제가 아니라 BR-014 판정이므로
 * 여기서 검증하지 않는다 — StoreSettingService가 BUSINESS_HOUR_INVALID로 판정한다.
 */
public record UpdateStoreSettingsRequest(
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime openTime,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime closeTime,
        @NotNull @Min(1) @Max(32767) Integer defaultSlotCapacity,
        @NotNull List<@NotNull LocalDate> holidays) {
}
