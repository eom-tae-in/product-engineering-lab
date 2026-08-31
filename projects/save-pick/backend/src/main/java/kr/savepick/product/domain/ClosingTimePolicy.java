package kr.savepick.product.domain;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * BR-003 — 마감 시각은 현재보다 미래여야 하고, 시각(시:분) 부분이 매장 영업 종료 시각을
 * 넘지 않아야 한다. 순수 도메인 정책이며 Clock이나 store 조회를 직접 하지 않고 필요한 값을
 * 인자로 받는다 (14-project-structure.md §9.1).
 */
public final class ClosingTimePolicy {

    private ClosingTimePolicy() {
    }

    public static boolean isValid(LocalDateTime closingAt, LocalDateTime now, LocalTime storeCloseTime) {
        if (closingAt == null || now == null || storeCloseTime == null) {
            return false;
        }
        if (!closingAt.isAfter(now)) {
            return false;
        }
        return !closingAt.toLocalTime().isAfter(storeCloseTime);
    }
}
