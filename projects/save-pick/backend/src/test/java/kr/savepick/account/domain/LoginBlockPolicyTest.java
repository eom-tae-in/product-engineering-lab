package kr.savepick.account.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 12-auth.md §1.2. DB 없이 정책 자체만 검증한다 (14-project-structure.md §10 domain/).
 */
class LoginBlockPolicyTest {

    private final LoginBlockPolicy policy = new LoginBlockPolicy(5, Duration.ofMinutes(10));
    private final LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Test
    @DisplayName("연속_5회_실패_10분_이내면_차단된다")
    void 연속_5회_실패_10분_이내면_차단된다() {
        List<LoginAttempt> attempts = List.of(
                LoginAttempt.record("a@a.com", null, false, now.minusMinutes(1)),
                LoginAttempt.record("a@a.com", null, false, now.minusMinutes(2)),
                LoginAttempt.record("a@a.com", null, false, now.minusMinutes(3)),
                LoginAttempt.record("a@a.com", null, false, now.minusMinutes(4)),
                LoginAttempt.record("a@a.com", null, false, now.minusMinutes(5)));

        LoginBlockPolicy.BlockDecision decision = policy.evaluate(attempts, now);

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.retryAfterAt()).isEqualTo(now.minusMinutes(5).plusMinutes(10));
    }

    @Test
    @DisplayName("차단_시간이_지나면_해제된다")
    void 차단_시간이_지나면_해제된다() {
        List<LoginAttempt> attempts = List.of(
                LoginAttempt.record("a@a.com", null, false, now.minusMinutes(11)),
                LoginAttempt.record("a@a.com", null, false, now.minusMinutes(12)),
                LoginAttempt.record("a@a.com", null, false, now.minusMinutes(13)),
                LoginAttempt.record("a@a.com", null, false, now.minusMinutes(14)),
                LoginAttempt.record("a@a.com", null, false, now.minusMinutes(15)));

        assertThat(policy.evaluate(attempts, now).blocked()).isFalse();
    }

    @Test
    @DisplayName("최근_시도에_성공이_섞이면_차단하지_않는다")
    void 최근_시도에_성공이_섞이면_차단하지_않는다() {
        List<LoginAttempt> attempts = List.of(
                LoginAttempt.record("a@a.com", 1L, false, now.minusMinutes(1)),
                LoginAttempt.record("a@a.com", 1L, true, now.minusMinutes(2)),
                LoginAttempt.record("a@a.com", null, false, now.minusMinutes(3)),
                LoginAttempt.record("a@a.com", null, false, now.minusMinutes(4)),
                LoginAttempt.record("a@a.com", null, false, now.minusMinutes(5)));

        assertThat(policy.evaluate(attempts, now).blocked()).isFalse();
    }

    @Test
    @DisplayName("시도가_임계값_미만이면_차단하지_않는다")
    void 시도가_임계값_미만이면_차단하지_않는다() {
        List<LoginAttempt> attempts = List.of(
                LoginAttempt.record("a@a.com", null, false, now),
                LoginAttempt.record("a@a.com", null, false, now));

        assertThat(policy.evaluate(attempts, now).blocked()).isFalse();
    }
}
