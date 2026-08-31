package kr.savepick.account.infrastructure;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 12-auth.md §1.4 — sp_refresh 쿠키 속성. 세 진입점(가입·로그인·재발급)이 공유한다.
 */
@Component
public class RefreshCookieFactory {

    public static final String COOKIE_NAME = "sp_refresh";

    private final Duration refreshSessionTtl;

    public RefreshCookieFactory(@Value("${savepick.auth.refresh-session-ttl}") String refreshSessionTtl) {
        this.refreshSessionTtl = Duration.parse(refreshSessionTtl);
    }

    public ResponseCookie build(String rawRefreshToken) {
        return ResponseCookie.from(COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api")
                .maxAge(refreshSessionTtl)
                .build();
    }

    public ResponseCookie clear() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api")
                .maxAge(0)
                .build();
    }
}
