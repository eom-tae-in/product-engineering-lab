package kr.savepick.product.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BR-003 (docs/16-test-plan.md TC-072 상품 등록 값 검증).
 */
class ClosingTimePolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 28, 12, 0, 0);
    private static final LocalTime STORE_CLOSE = LocalTime.of(22, 0);

    @Test
    @DisplayName("TC_072a_과거_시각은_거부한다")
    void TC_072a_과거_시각은_거부한다() {
        assertThat(ClosingTimePolicy.isValid(NOW.minusMinutes(1), NOW, STORE_CLOSE)).isFalse();
    }

    @Test
    @DisplayName("현재_시각과_같아도_거부한다")
    void 현재_시각과_같아도_거부한다() {
        assertThat(ClosingTimePolicy.isValid(NOW, NOW, STORE_CLOSE)).isFalse();
    }

    @Test
    @DisplayName("TC_072b_영업_종료_시각을_넘으면_거부한다")
    void TC_072b_영업_종료_시각을_넘으면_거부한다() {
        LocalDateTime closingAt = NOW.toLocalDate().atTime(22, 1);
        assertThat(ClosingTimePolicy.isValid(closingAt, NOW, STORE_CLOSE)).isFalse();
    }

    @Test
    @DisplayName("영업_종료_시각과_정확히_같으면_허용한다")
    void 영업_종료_시각과_정확히_같으면_허용한다() {
        LocalDateTime closingAt = NOW.toLocalDate().atTime(22, 0);
        assertThat(ClosingTimePolicy.isValid(closingAt, NOW, STORE_CLOSE)).isTrue();
    }

    @Test
    @DisplayName("미래이고_영업시간_이내면_허용한다")
    void 미래이고_영업시간_이내면_허용한다() {
        assertThat(ClosingTimePolicy.isValid(NOW.plusHours(3), NOW, STORE_CLOSE)).isTrue();
    }
}
