package kr.savepick.account.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.savepick.account.domain.AuthSession;
import kr.savepick.account.domain.LoginAttempt;
import kr.savepick.account.domain.LoginAttemptRepository;
import kr.savepick.account.domain.LoginBlockPolicy;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.account.domain.Role;
import kr.savepick.account.infrastructure.AuthSessionJpaRepository;
import kr.savepick.account.infrastructure.BcryptPasswordHasher;
import kr.savepick.account.infrastructure.JwtAccessTokenIssuer;
import kr.savepick.account.infrastructure.RefreshTokenHasher;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-002 고객 로그인, API-101 관리자 로그인이 공유한다 (12-auth.md §1.2, §2.5).
 * 두 경로 모두 login_attempts를 공유해 차단을 우회할 수 없다.
 */
@Service
public class LoginService {

    private final MemberRepository memberRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final AuthSessionJpaRepository authSessionRepository;
    private final BcryptPasswordHasher passwordHasher;
    private final RefreshTokenHasher refreshTokenHasher;
    private final JwtAccessTokenIssuer tokenIssuer;
    private final ServerClock serverClock;
    private final LoginBlockPolicy loginBlockPolicy;
    private final Duration refreshSessionTtl;

    public LoginService(
            MemberRepository memberRepository,
            LoginAttemptRepository loginAttemptRepository,
            AuthSessionJpaRepository authSessionRepository,
            BcryptPasswordHasher passwordHasher,
            RefreshTokenHasher refreshTokenHasher,
            JwtAccessTokenIssuer tokenIssuer,
            ServerClock serverClock,
            @Value("${savepick.auth.login-fail-threshold}") int loginFailThreshold,
            @Value("${savepick.auth.login-block-duration}") String loginBlockDuration,
            @Value("${savepick.auth.refresh-session-ttl}") String refreshSessionTtl) {
        this.memberRepository = memberRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordHasher = passwordHasher;
        this.refreshTokenHasher = refreshTokenHasher;
        this.tokenIssuer = tokenIssuer;
        this.serverClock = serverClock;
        this.loginBlockPolicy = new LoginBlockPolicy(loginFailThreshold, Duration.parse(loginBlockDuration));
        this.refreshSessionTtl = Duration.parse(refreshSessionTtl);
    }

    /**
     * @param expectedRole API-002면 CUSTOMER, API-101이면 ADMIN. 비밀번호는 맞는데 역할이 다르면 FORBIDDEN(403) —
     *                     자격 증명 실패가 아니라 권한 문제이므로 login_attempts에는 succeeded=true로 남긴다 (12번 S6).
     */
    @Transactional
    public LoginResult login(String rawEmail, String rawPassword, Role expectedRole, String userAgent) {
        String email = rawEmail.toLowerCase();
        LocalDateTime now = serverClock.now();

        List<LoginAttempt> recentAttempts = loginAttemptRepository.findTop5ByEmailOrderByAttemptedAtDesc(email);
        LoginBlockPolicy.BlockDecision decision = loginBlockPolicy.evaluate(recentAttempts, now);
        if (decision.blocked()) {
            throw new BusinessException(ErrorCode.LOGIN_BLOCKED, ErrorCode.LOGIN_BLOCKED.defaultMessage(),
                    Map.of("retryAfterAt", decision.retryAfterAt().atZone(serverClock.zone()).toOffsetDateTime()));
        }

        Optional<Member> memberOpt = memberRepository.findByEmail(email);
        if (memberOpt.isEmpty()) {
            passwordHasher.compareAgainstDummy(rawPassword);
            loginAttemptRepository.save(LoginAttempt.record(email, null, false, now));
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        Member member = memberOpt.get();
        boolean passwordOk = passwordHasher.matches(rawPassword, member.getPasswordHash());
        loginAttemptRepository.save(LoginAttempt.record(email, member.getId(), passwordOk, now));
        if (!passwordOk) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (member.getRole() != expectedRole) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        String rawRefreshToken = refreshTokenHasher.generateToken();
        LocalDateTime sessionExpiresAt = now.plus(refreshSessionTtl);
        AuthSession session = AuthSession.issue(
                member.getId(), refreshTokenHasher.hash(rawRefreshToken), now, sessionExpiresAt, userAgent);
        session = authSessionRepository.save(session);

        JwtAccessTokenIssuer.IssuedAccessToken issued =
                tokenIssuer.issue(member.getId(), member.getRole(), session.getId(), now);

        return new LoginResult(member, issued.token(), issued.expiresAt(), rawRefreshToken, sessionExpiresAt);
    }

    public record LoginResult(
            Member member,
            String accessToken,
            LocalDateTime accessTokenExpiresAt,
            String rawRefreshToken,
            LocalDateTime refreshTokenExpiresAt) {
    }
}
