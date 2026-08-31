package kr.savepick.account.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import kr.savepick.account.application.LoginService;
import kr.savepick.account.application.LogoutService;
import kr.savepick.account.application.SignUpService;
import kr.savepick.account.application.TokenRefreshService;
import kr.savepick.account.domain.Role;
import kr.savepick.account.infrastructure.AuthenticatedPrincipal;
import kr.savepick.account.infrastructure.RefreshCookieFactory;
import kr.savepick.cart.application.CartMergeService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-001~004 (11-api-spec.md §1, 12-auth.md).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SignUpService signUpService;
    private final LoginService loginService;
    private final TokenRefreshService tokenRefreshService;
    private final LogoutService logoutService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final CartMergeService cartMergeService;

    public AuthController(
            SignUpService signUpService,
            LoginService loginService,
            TokenRefreshService tokenRefreshService,
            LogoutService logoutService,
            RefreshCookieFactory refreshCookieFactory,
            CartMergeService cartMergeService) {
        this.signUpService = signUpService;
        this.loginService = loginService;
        this.tokenRefreshService = tokenRefreshService;
        this.logoutService = logoutService;
        this.refreshCookieFactory = refreshCookieFactory;
        this.cartMergeService = cartMergeService;
    }

    /** API-001. guestToken이 있으면 회원 확정 뒤 그 게스트 장바구니를 병합한다(10-erd.md §5.1). */
    @PostMapping("/signup")
    public ResponseEntity<SignUpResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        SignUpService.SignUpResult result =
                signUpService.signUp(request.email(), request.password(), request.name(), request.phone());
        boolean cartMerged = mergeGuestCartIfPresent(result.member().getId(), request.guestToken());
        SignUpResponse body = new SignUpResponse(
                result.member().getId(), result.member().getEmail(), result.member().getName(),
                result.member().getRole(), result.accessToken(), toOffset(result.accessTokenExpiresAt()), cartMerged);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.build(result.rawRefreshToken()).toString())
                .body(body);
    }

    /** API-002. guestToken이 있으면 로그인 확정 뒤 그 게스트 장바구니를 병합한다(10-erd.md §5.1). */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                                @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        LoginService.LoginResult result =
                loginService.login(request.email(), request.password(), Role.CUSTOMER, userAgent);
        mergeGuestCartIfPresent(result.member().getId(), request.guestToken());
        LoginResponse body = new LoginResponse(
                result.member().getId(), result.member().getName(), result.member().getRole(),
                result.accessToken(), toOffset(result.accessTokenExpiresAt()), result.member().getOrderPermission());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.build(result.rawRefreshToken()).toString())
                .body(body);
    }

    /**
     * guestToken 형식이 아니면 조용히 무시한다 — 병합 실패로 가입·로그인 자체가 막히면 안 된다
     * (12-auth.md 원칙과 같은 "부가 기능이 핵심 흐름을 막지 않는다"는 원칙을 따른다).
     */
    private boolean mergeGuestCartIfPresent(Long memberId, String rawGuestToken) {
        if (rawGuestToken == null || rawGuestToken.isBlank()) {
            return false;
        }
        try {
            UUID guestToken = UUID.fromString(rawGuestToken);
            return cartMergeService.mergeGuestCartIntoMember(memberId, guestToken);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** API-003. */
    @PostMapping("/token/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(name = RefreshCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        TokenRefreshService.RefreshResult result = tokenRefreshService.refresh(refreshToken);
        RefreshResponse body = new RefreshResponse(result.accessToken(), toOffset(result.accessTokenExpiresAt()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.build(result.rawRefreshToken()).toString())
                .body(body);
    }

    /** API-004. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        logoutService.logout(principal.sessionId());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear().toString())
                .build();
    }

    private java.time.OffsetDateTime toOffset(java.time.LocalDateTime ldt) {
        return ldt.atZone(java.time.ZoneId.of("Asia/Seoul")).toOffsetDateTime();
    }
}
