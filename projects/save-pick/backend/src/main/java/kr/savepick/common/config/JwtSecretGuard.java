package kr.savepick.common.config;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 로컬 개발용 기본 JWT 시크릿으로 운영에 뜨는 것을 막는다 (12-auth.md §5 P8).
 *
 * <p>{@code application.yml}의 {@code savepick.auth.jwt-secret}은 환경변수로 덮어쓰도록
 * 돼 있지만 기본값이 저장소에 그대로 커밋된다. 배포할 때 {@code JWT_SECRET}을 빠뜨려도
 * 애플리케이션은 아무 일 없다는 듯 정상 기동하고, 그 순간 저장소를 읽을 수 있는 누구나
 * 임의의 회원 ID·역할로 액세스 토큰을 위조할 수 있다. 조용히 열려 있는 문이라 더 위험해
 * 기동 자체를 거부한다.
 *
 * <p>판정 기준은 활성 프로파일이다. {@code dev}·{@code test}만 켜져 있으면 로컬 실행으로
 * 보고 통과시킨다. 프로파일이 하나도 없으면 운영일 수 있으므로 막는다 —
 * 보안 가드는 애매할 때 닫는 쪽이 맞다.
 */
@Component
public class JwtSecretGuard {

    /** application.yml에 커밋돼 있는 로컬 개발용 기본값. */
    static final String LOCAL_DEV_SECRET = "local-dev-only-secret-key-please-override-in-production-32bytes+";

    private static final Set<String> LOCAL_PROFILES = Set.of("dev", "test");

    private final String jwtSecret;
    private final Environment environment;

    public JwtSecretGuard(@Value("${savepick.auth.jwt-secret}") String jwtSecret, Environment environment) {
        this.jwtSecret = jwtSecret;
        this.environment = environment;
    }

    @PostConstruct
    void rejectLocalSecretOutsideLocalProfiles() {
        if (!LOCAL_DEV_SECRET.equals(jwtSecret)) {
            return;
        }
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        if (!activeProfiles.isEmpty() && LOCAL_PROFILES.containsAll(activeProfiles)) {
            return;
        }
        throw new IllegalStateException("""
                JWT_SECRET 환경변수가 설정되지 않아 로컬 개발용 기본 시크릿으로 기동하려 했습니다.
                이 값은 저장소에 공개돼 있어 누구나 액세스 토큰을 위조할 수 있습니다.

                  - 운영·스테이징: JWT_SECRET에 32바이트 이상의 임의 값을 설정하세요.
                  - 로컬 개발: SPRING_PROFILES_ACTIVE=dev 로 실행하세요(README 실행 방법).

                현재 활성 프로파일: %s""".formatted(activeProfiles.isEmpty() ? "(없음)" : String.join(",", activeProfiles)));
    }
}
