package kr.savepick.account.application;

import java.time.Duration;
import java.time.LocalDateTime;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.account.infrastructure.AuthSessionJpaRepository;
import kr.savepick.account.infrastructure.BcryptPasswordHasher;
import kr.savepick.account.infrastructure.JwtAccessTokenIssuer;
import kr.savepick.account.infrastructure.RefreshTokenHasher;
import kr.savepick.account.domain.AuthSession;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-001 회원가입 (11-api-spec.md, FR-001, BR-002).
 */
@Service
public class SignUpService {

    private final MemberRepository memberRepository;
    private final AuthSessionJpaRepository authSessionRepository;
    private final BcryptPasswordHasher passwordHasher;
    private final RefreshTokenHasher refreshTokenHasher;
    private final JwtAccessTokenIssuer tokenIssuer;
    private final ServerClock serverClock;
    private final Duration refreshSessionTtl;

    public SignUpService(
            MemberRepository memberRepository,
            AuthSessionJpaRepository authSessionRepository,
            BcryptPasswordHasher passwordHasher,
            RefreshTokenHasher refreshTokenHasher,
            JwtAccessTokenIssuer tokenIssuer,
            ServerClock serverClock,
            @Value("${savepick.auth.refresh-session-ttl}") String refreshSessionTtl) {
        this.memberRepository = memberRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordHasher = passwordHasher;
        this.refreshTokenHasher = refreshTokenHasher;
        this.tokenIssuer = tokenIssuer;
        this.serverClock = serverClock;
        this.refreshSessionTtl = Duration.parse(refreshSessionTtl);
    }

    @Transactional
    public SignUpResult signUp(String rawEmail, String rawPassword, String name, String phone) {
        String email = rawEmail.toLowerCase();
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }

        LocalDateTime now = serverClock.now();
        Member member = Member.registerCustomer(email, passwordHasher.hash(rawPassword), name, phone, now);
        member = memberRepository.save(member);

        String rawRefreshToken = refreshTokenHasher.generateToken();
        LocalDateTime sessionExpiresAt = now.plus(refreshSessionTtl);
        AuthSession session = AuthSession.issue(
                member.getId(), refreshTokenHasher.hash(rawRefreshToken), now, sessionExpiresAt, null);
        session = authSessionRepository.save(session);

        JwtAccessTokenIssuer.IssuedAccessToken issued =
                tokenIssuer.issue(member.getId(), member.getRole(), session.getId(), now);

        return new SignUpResult(member, issued.token(), issued.expiresAt(), rawRefreshToken, sessionExpiresAt);
    }

    public record SignUpResult(
            Member member,
            String accessToken,
            LocalDateTime accessTokenExpiresAt,
            String rawRefreshToken,
            LocalDateTime refreshTokenExpiresAt) {
    }
}
