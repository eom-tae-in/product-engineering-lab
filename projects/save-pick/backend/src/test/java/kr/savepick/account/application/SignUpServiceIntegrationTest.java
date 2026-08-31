package kr.savepick.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.savepick.account.domain.MemberRepository;
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

/** API-001 (14-project-structure.md §10 application/). */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class SignUpServiceIntegrationTest {

    @Autowired
    private SignUpService signUpService;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("가입에_성공하면_토큰과_리프레시_세션이_발급된다")
    void 가입에_성공하면_토큰과_리프레시_세션이_발급된다() {
        var result = signUpService.signUp("new@test.com", "password123", "홍길동", "01011112222");

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.rawRefreshToken()).isNotBlank();
        assertThat(memberRepository.findByEmail("new@test.com")).isPresent();
        assertThat(result.member().getRole().name()).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("이미_가입된_이메일이면_EMAIL_DUPLICATED를_반환한다")
    void 이미_가입된_이메일이면_EMAIL_DUPLICATED를_반환한다() {
        signUpService.signUp("dup@test.com", "password123", "홍길동", "01011112222");

        assertThatThrownBy(() -> signUpService.signUp("dup@test.com", "password456", "김철수", "01033334444"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.EMAIL_DUPLICATED);
    }

    @Test
    @DisplayName("이메일은_소문자로_정규화된다")
    void 이메일은_소문자로_정규화된다() {
        signUpService.signUp("Mixed@Test.com", "password123", "홍길동", "01011112222");

        assertThat(memberRepository.findByEmail("mixed@test.com")).isPresent();
    }
}
