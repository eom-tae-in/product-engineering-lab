package kr.savepick.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * 12-auth.md §5 P8 — 공개된 개발용 기본 시크릿으로 운영에 뜨지 못하게 막는다.
 * Spring 컨텍스트 없이 판정 로직만 직접 검증한다(무엇을 막고 무엇을 통과시키는지가 핵심).
 */
class JwtSecretGuardTest {

    private static final String REAL_SECRET = "a-real-32-byte-or-longer-secret-value-for-production";

    private JwtSecretGuard guard(String secret, String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return new JwtSecretGuard(secret, environment);
    }

    @Test
    @DisplayName("기본_시크릿이면_프로파일이_없을_때_기동을_거부한다")
    void 기본_시크릿이면_프로파일이_없을_때_기동을_거부한다() {
        assertThatThrownBy(() -> guard(JwtSecretGuard.LOCAL_DEV_SECRET).rejectLocalSecretOutsideLocalProfiles())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("기본_시크릿이면_운영_프로파일에서_기동을_거부한다")
    void 기본_시크릿이면_운영_프로파일에서_기동을_거부한다() {
        assertThatThrownBy(() -> guard(JwtSecretGuard.LOCAL_DEV_SECRET, "prod").rejectLocalSecretOutsideLocalProfiles())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("dev와_test에_섞여_운영_프로파일이_함께_켜져도_거부한다")
    void dev와_test에_섞여_운영_프로파일이_함께_켜져도_거부한다() {
        assertThatThrownBy(() -> guard(JwtSecretGuard.LOCAL_DEV_SECRET, "dev", "prod").rejectLocalSecretOutsideLocalProfiles())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("기본_시크릿이어도_dev_test_프로파일이면_통과한다")
    void 기본_시크릿이어도_dev_test_프로파일이면_통과한다() {
        assertThatCode(() -> guard(JwtSecretGuard.LOCAL_DEV_SECRET, "dev").rejectLocalSecretOutsideLocalProfiles())
                .doesNotThrowAnyException();
        assertThatCode(() -> guard(JwtSecretGuard.LOCAL_DEV_SECRET, "test").rejectLocalSecretOutsideLocalProfiles())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("실제_시크릿을_넣으면_프로파일과_무관하게_통과한다")
    void 실제_시크릿을_넣으면_프로파일과_무관하게_통과한다() {
        assertThatCode(() -> guard(REAL_SECRET).rejectLocalSecretOutsideLocalProfiles())
                .doesNotThrowAnyException();
        assertThatCode(() -> guard(REAL_SECRET, "prod").rejectLocalSecretOutsideLocalProfiles())
                .doesNotThrowAnyException();
    }
}
