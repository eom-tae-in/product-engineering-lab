package kr.savepick.account.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 12-auth.md §5 P7 — 관리자 부트스트랩 설정을 언제 무시하고 언제 기동을 막는지 검증한다.
 * 설정 판정만 다루므로 Spring 컨텍스트를 띄우지 않는다(JwtSecretGuardTest와 같은 방식).
 * 계정 생성은 {@link kr.savepick.account.application.AdminAccountBootstrapService} 통합 테스트가 맡는다.
 */
class AdminBootstrapRunnerTest {

    /** 설정 판정 메서드만 호출하므로 서비스는 쓰이지 않는다. */
    private AdminBootstrapRunner runner(String email, String password, String name, String phone) {
        return new AdminBootstrapRunner(null, email, password, name, phone);
    }

    @Test
    @DisplayName("네_값이_모두_비면_꺼진_것으로_보고_아무것도_하지_않는다")
    void 네_값이_모두_비면_꺼진_것으로_보고_아무것도_하지_않는다() {
        assertThat(runner("", "", "", "").isDisabled()).isTrue();
    }

    @Test
    @DisplayName("공백만_있는_값도_빈_값으로_본다")
    void 공백만_있는_값도_빈_값으로_본다() {
        assertThat(runner("   ", "", "", "").isDisabled()).isTrue();
    }

    @Test
    @DisplayName("이메일만_넣고_비밀번호를_빠뜨리면_기동을_거부한다")
    void 이메일만_넣고_비밀번호를_빠뜨리면_기동을_거부한다() {
        AdminBootstrapRunner runner = runner("admin@savepick.kr", "", "매장 관리자", "01099998888");

        assertThat(runner.isDisabled()).isFalse();
        assertThatThrownBy(runner::rejectInvalidValues)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_PASSWORD");
    }

    @Test
    @DisplayName("비밀번호가_8자보다_짧으면_기동을_거부한다")
    void 비밀번호가_8자보다_짧으면_기동을_거부한다() {
        assertThatThrownBy(() -> runner("admin@savepick.kr", "short1", "매장 관리자", "01099998888").rejectInvalidValues())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("8자");
    }

    @Test
    @DisplayName("이메일_형식이_아니면_기동을_거부한다")
    void 이메일_형식이_아니면_기동을_거부한다() {
        assertThatThrownBy(() -> runner("admin", "adminpass1", "매장 관리자", "01099998888").rejectInvalidValues())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_EMAIL");
    }

    @Test
    @DisplayName("휴대폰_번호_형식이_아니면_기동을_거부한다")
    void 휴대폰_번호_형식이_아니면_기동을_거부한다() {
        assertThatThrownBy(() -> runner("admin@savepick.kr", "adminpass1", "매장 관리자", "021234567").rejectInvalidValues())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_PHONE");
    }

    @Test
    @DisplayName("이름이_비면_기동을_거부한다")
    void 이름이_비면_기동을_거부한다() {
        assertThatThrownBy(() -> runner("admin@savepick.kr", "adminpass1", "", "01099998888").rejectInvalidValues())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_NAME");
    }

    @Test
    @DisplayName("네_값이_모두_올바르면_통과한다")
    void 네_값이_모두_올바르면_통과한다() {
        AdminBootstrapRunner runner = runner("admin@savepick.kr", "adminpass1", "매장 관리자", "01099998888");

        assertThat(runner.isDisabled()).isFalse();
        assertThatCode(runner::rejectInvalidValues).doesNotThrowAnyException();
    }
}
