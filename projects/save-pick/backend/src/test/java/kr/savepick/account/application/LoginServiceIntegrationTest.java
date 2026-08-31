package kr.savepick.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.account.domain.Role;
import kr.savepick.account.infrastructure.BcryptPasswordHasher;
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

/** API-002, API-101 (12-auth.md §1.2, §2.5). */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class LoginServiceIntegrationTest {

    @Autowired
    private LoginService loginService;

    @Autowired
    private SignUpService signUpService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BcryptPasswordHasher passwordHasher;

    @Test
    @DisplayName("올바른_자격증명이면_로그인에_성공한다")
    void 올바른_자격증명이면_로그인에_성공한다() {
        signUpService.signUp("user1@test.com", "password123", "사용자1", "01011112222");

        var result = loginService.login("user1@test.com", "password123", Role.CUSTOMER, "junit-agent");

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.member().getEmail()).isEqualTo("user1@test.com");
    }

    @Test
    @DisplayName("비밀번호가_틀리면_INVALID_CREDENTIALS이다")
    void 비밀번호가_틀리면_INVALID_CREDENTIALS이다() {
        signUpService.signUp("user2@test.com", "password123", "사용자2", "01011112222");

        assertThatThrownBy(() -> loginService.login("user2@test.com", "wrongpass1", Role.CUSTOMER, "junit-agent"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("미가입_이메일도_동일한_오류를_반환한다")
    void 미가입_이메일도_동일한_오류를_반환한다() {
        assertThatThrownBy(() -> loginService.login("nobody@test.com", "whatever1", Role.CUSTOMER, "junit-agent"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("연속_5회_실패하면_LOGIN_BLOCKED이다")
    void 연속_5회_실패하면_LOGIN_BLOCKED이다() {
        signUpService.signUp("user3@test.com", "password123", "사용자3", "01011112222");

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> loginService.login("user3@test.com", "wrongpass1", Role.CUSTOMER, "junit-agent"))
                    .isInstanceOf(BusinessException.class);
        }

        assertThatThrownBy(() -> loginService.login("user3@test.com", "password123", Role.CUSTOMER, "junit-agent"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.LOGIN_BLOCKED);
    }

    @Test
    @DisplayName("관리자_계정이_고객_로그인_경로로_접근하면_FORBIDDEN이다")
    void 관리자_계정이_고객_로그인_경로로_접근하면_FORBIDDEN이다() {
        Member admin = Member.registerAdmin(
                "admin1@test.com", passwordHasher.hash("adminpass1"), "관리자", "01099998888", LocalDateTime.now());
        memberRepository.save(admin);

        assertThatThrownBy(() -> loginService.login("admin1@test.com", "adminpass1", Role.CUSTOMER, "junit-agent"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("고객_계정이_관리자_로그인_경로로_접근하면_FORBIDDEN이다")
    void 고객_계정이_관리자_로그인_경로로_접근하면_FORBIDDEN이다() {
        signUpService.signUp("user4@test.com", "password123", "사용자4", "01011112222");

        assertThatThrownBy(() -> loginService.login("user4@test.com", "password123", Role.ADMIN, "junit-agent"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}
