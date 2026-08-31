package kr.savepick.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** BR-018 순수 정책 단위 테스트(DB 없음). */
class CancelDeadlinePolicyTest {

    @Test
    @DisplayName("취소_가능_시각은_픽업_시작_1시간_전이다")
    void 취소_가능_시각은_픽업_시작_1시간_전이다() {
        LocalDateTime pickupStartAt = LocalDateTime.of(2026, 8, 28, 19, 30);

        LocalDateTime cancelableUntil = CancelDeadlinePolicy.cancelableUntil(pickupStartAt, Duration.ofHours(1));

        assertThat(cancelableUntil).isEqualTo(LocalDateTime.of(2026, 8, 28, 18, 30));
    }

    @Test
    @DisplayName("마감_시각_이전이면_취소_가능하다")
    void 마감_시각_이전이면_취소_가능하다() {
        LocalDateTime cancelableUntil = LocalDateTime.of(2026, 8, 28, 18, 30);
        assertThat(CancelDeadlinePolicy.isCancelable(cancelableUntil.minusMinutes(1), cancelableUntil)).isTrue();
        assertThat(CancelDeadlinePolicy.isCancelable(cancelableUntil, cancelableUntil)).isTrue();
    }

    @Test
    @DisplayName("마감_시각이_지나면_취소_불가하다")
    void 마감_시각이_지나면_취소_불가하다() {
        LocalDateTime cancelableUntil = LocalDateTime.of(2026, 8, 28, 18, 30);
        assertThat(CancelDeadlinePolicy.isCancelable(cancelableUntil.plusSeconds(1), cancelableUntil)).isFalse();
    }
}
