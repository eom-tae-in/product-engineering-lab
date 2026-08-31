package kr.savepick.account.api;

import jakarta.validation.Valid;
import kr.savepick.account.application.LoginService;
import kr.savepick.account.domain.Role;
import kr.savepick.account.infrastructure.RefreshCookieFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-101 관리자 로그인 (11-api-spec.md §6, 12-auth.md §2.5) — 고객과 진입 경로를 분리한다.
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final LoginService loginService;
    private final RefreshCookieFactory refreshCookieFactory;

    public AdminAuthController(LoginService loginService, RefreshCookieFactory refreshCookieFactory) {
        this.loginService = loginService;
        this.refreshCookieFactory = refreshCookieFactory;
    }

    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        LoginService.LoginResult result = loginService.login(request.email(), request.password(), Role.ADMIN, userAgent);
        AdminLoginResponse body = new AdminLoginResponse(
                result.member().getId(), result.member().getName(), result.member().getRole(),
                result.accessToken(),
                result.accessTokenExpiresAt().atZone(java.time.ZoneId.of("Asia/Seoul")).toOffsetDateTime());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.build(result.rawRefreshToken()).toString())
                .body(body);
    }
}
