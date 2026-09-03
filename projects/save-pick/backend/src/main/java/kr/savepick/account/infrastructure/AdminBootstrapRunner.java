package kr.savepick.account.infrastructure;

import java.util.regex.Pattern;
import kr.savepick.account.application.AdminAccountBootstrapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 기동 시 환경변수로 받은 첫 관리자 계정을 만든다 (12-auth.md §5 P7).
 *
 * <p>기본 상태는 <b>꺼져 있다</b> — {@code ADMIN_BOOTSTRAP_EMAIL}과
 * {@code ADMIN_BOOTSTRAP_PASSWORD}가 모두 비어 있으면 아무 일도 하지 않는다. 관리자 계정은
 * 배포마다 만들 것이 아니라 처음 한 번만 필요하고, 켜져 있는 것이 기본이면 운영자가 잊고
 * 놔둔 환경변수가 계정을 계속 되살리는 경로가 되기 때문이다.
 *
 * <p>값이 있는데 형식이 틀리면 <b>기동을 거부한다</b>. 관리자 계정을 만들려고 값을 넣었는데
 * 조용히 무시되면, 운영자는 배포가 끝난 뒤 로그인 화면에서야 실패를 알게 된다.
 * {@code JwtSecretGuard}와 같은 판단이다 — 애매하면 기동을 멈추는 쪽이 낫다.
 *
 * <p>계정이 이미 있으면 아무것도 바꾸지 않는다. 재기동해도 안전하다.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    /** 12-auth.md §1.1 — 비밀번호 최소 길이 8자 (03 A2, FR-001). 고객 가입과 같은 기준을 쓴다. */
    static final int MIN_PASSWORD_LENGTH = 8;

    /** SignUpRequest와 같은 규칙. 관리자만 다른 형식을 쓸 이유가 없다. */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^01\\d{8,9}$");

    private final AdminAccountBootstrapService bootstrapService;
    private final String email;
    private final String password;
    private final String name;
    private final String phone;

    public AdminBootstrapRunner(
            AdminAccountBootstrapService bootstrapService,
            @Value("${savepick.admin.bootstrap.email:}") String email,
            @Value("${savepick.admin.bootstrap.password:}") String password,
            @Value("${savepick.admin.bootstrap.name:}") String name,
            @Value("${savepick.admin.bootstrap.phone:}") String phone) {
        this.bootstrapService = bootstrapService;
        this.email = email.trim();
        this.password = password;
        this.name = name.trim();
        this.phone = phone.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isDisabled()) {
            return;
        }
        rejectInvalidValues();

        AdminAccountBootstrapService.Result result =
                bootstrapService.createIfAbsent(email, password, name, phone);
        if (result == AdminAccountBootstrapService.Result.CREATED) {
            log.info("관리자 계정을 만들었습니다 ({}). ADMIN_BOOTSTRAP_* 환경변수를 지우고 다시 배포하세요.", email);
        } else {
            log.info("관리자 계정 {}이(가) 이미 있어 부트스트랩을 건너뜁니다.", email);
        }
    }

    /** 이메일·비밀번호가 모두 비어 있을 때만 꺼진 것으로 본다. 한쪽만 넣은 것은 설정 실수다. */
    boolean isDisabled() {
        return email.isEmpty() && password.isEmpty();
    }

    void rejectInvalidValues() {
        String problem = findProblem();
        if (problem == null) {
            return;
        }
        throw new IllegalStateException("""
                관리자 계정 부트스트랩 설정이 올바르지 않아 기동하지 않습니다: %s

                네 값을 모두 채우거나, 만들지 않으려면 넷 다 비우세요.
                  ADMIN_BOOTSTRAP_EMAIL     관리자 로그인 이메일
                  ADMIN_BOOTSTRAP_PASSWORD  %d자 이상
                  ADMIN_BOOTSTRAP_NAME      관리자 이름
                  ADMIN_BOOTSTRAP_PHONE     01로 시작하는 숫자 10~11자리

                계정을 만든 뒤에는 네 값을 지우고 다시 배포하세요.""".formatted(problem, MIN_PASSWORD_LENGTH));
    }

    private String findProblem() {
        if (email.isEmpty()) {
            return "ADMIN_BOOTSTRAP_EMAIL이 비어 있습니다";
        }
        if (!email.contains("@") || email.contains(" ")) {
            return "ADMIN_BOOTSTRAP_EMAIL이 이메일 형식이 아닙니다";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "ADMIN_BOOTSTRAP_PASSWORD가 %d자보다 짧습니다".formatted(MIN_PASSWORD_LENGTH);
        }
        if (name.isEmpty()) {
            return "ADMIN_BOOTSTRAP_NAME이 비어 있습니다";
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            return "ADMIN_BOOTSTRAP_PHONE이 휴대폰 번호 형식이 아닙니다";
        }
        return null;
    }
}
