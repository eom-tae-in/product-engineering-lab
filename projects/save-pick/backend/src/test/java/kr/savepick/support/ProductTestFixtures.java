package kr.savepick.support;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * BR-003(마감 시각은 매장 영업 종료 시각을 넘을 수 없다) 때문에 {@code now.plusHours(N)}을 곧바로
 * closingAt으로 쓰면 실행 시각에 따라 22:00을 넘어 테스트가 간헐적으로 실패할 수 있다(실제 시각
 * 기반 ServerClock을 쓰는 통합 테스트 한정 — 도메인 단위 테스트는 고정된 now를 쓰므로 해당 없음).
 * 이 헬퍼는 항상 "미래이고 매장 영업 종료 시각 이내"인 값을 돌려준다.
 */
public final class ProductTestFixtures {

    private static final LocalTime STORE_CLOSE_TIME = LocalTime.of(22, 0);

    private ProductTestFixtures() {
    }

    public static LocalDateTime futureClosingAt(LocalDateTime now, int hoursAhead) {
        LocalDateTime candidate = now.plusHours(hoursAhead);
        // 자정을 넘기면 candidate의 시각(예: 00:08)이 숫자상 STORE_CLOSE_TIME(22:00)보다
        // "이르게" 보여 아래 시각 비교만으로는 자정 통과를 감지하지 못한다 — 날짜가 바뀌었는지도
        // 함께 확인해야 한다. 그렇지 않으면 이 값에서 1시간을 빼는 다른 테스트(TC-074 등)가
        // "오늘 23시대"로 되돌아가 실제로 영업 종료 시각을 넘겨버리는 간헐적 실패가 생긴다.
        boolean crossedIntoNextDay = !candidate.toLocalDate().equals(now.toLocalDate());
        boolean afterStoreClose = candidate.toLocalTime().isAfter(STORE_CLOSE_TIME);
        if (crossedIntoNextDay || afterStoreClose) {
            return now.toLocalDate().plusDays(1).atTime(20, 0);
        }
        return candidate;
    }
}
