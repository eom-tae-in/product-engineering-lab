package kr.savepick.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** API-003 (12-auth.md §2.2 — 회전). */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class TokenRefreshServiceIntegrationTest {

    @Autowired
    private SignUpService signUpService;

    @Autowired
    private TokenRefreshService tokenRefreshService;

    @Test
    @DisplayName("재발급하면_리프레시_토큰이_회전한다")
    void 재발급하면_리프레시_토큰이_회전한다() {
        var signUp = signUpService.signUp("refresh1@test.com", "password123", "사용자", "01011112222");

        var refreshed = tokenRefreshService.refresh(signUp.rawRefreshToken());

        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.rawRefreshToken()).isNotEqualTo(signUp.rawRefreshToken());
    }

    @Test
    @DisplayName("회전_이후_이전_리프레시_토큰은_더_이상_쓸_수_없다")
    void 회전_이후_이전_리프레시_토큰은_더_이상_쓸_수_없다() {
        var signUp = signUpService.signUp("refresh2@test.com", "password123", "사용자", "01011112222");
        tokenRefreshService.refresh(signUp.rawRefreshToken());

        assertThatThrownBy(() -> tokenRefreshService.refresh(signUp.rawRefreshToken()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("존재하지_않는_토큰이면_UNAUTHENTICATED이다")
    void 존재하지_않는_토큰이면_UNAUTHENTICATED이다() {
        assertThatThrownBy(() -> tokenRefreshService.refresh("not-a-real-refresh-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }
}
