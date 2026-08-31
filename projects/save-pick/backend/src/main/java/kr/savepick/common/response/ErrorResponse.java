package kr.savepick.common.response;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 11-api-spec.md §0.3 공통 오류 응답 형식.
 */
public record ErrorResponse(
        String code,
        String message,
        OffsetDateTime serverTime,
        Map<String, Object> details
) {
}
