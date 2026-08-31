package kr.savepick.account.application;

import java.time.Duration;
import java.time.LocalDateTime;
import kr.savepick.account.domain.AuthSession;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.account.infrastructure.AuthSessionJpaRepository;
import kr.savepick.account.infrastructure.JwtAccessTokenIssuer;
import kr.savepick.account.infrastructure.RefreshTokenHasher;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-003 액세스 토큰 재발급 (12-auth.md §2.2) — 리프레시 토큰을 회전한다.
 */
@Service
public class TokenRefreshService {

    private final AuthSessionJpaRepository authSessionRepository;
    private final MemberRepository memberRepository;
    private final RefreshTokenHasher refreshTokenHasher;
    private final JwtAccessTokenIssuer tokenIssuer;
    private final ServerClock serverClock;
    private final Duration refreshSessionTtl;

    public TokenRefreshService(
            AuthSessionJpaRepository authSessionRepository,
            MemberRepository memberRepository,
            RefreshTokenHasher refreshTokenHasher,
            JwtAccessTokenIssuer tokenIssuer,
            ServerClock serverClock,
            @Value("${savepick.auth.refresh-session-ttl}") String refreshSessionTtl) {
        this.authSessionRepository = authSessionRepository;
        this.memberRepository = memberRepository;
        this.refreshTokenHasher = refreshTokenHasher;
        this.tokenIssuer = tokenIssuer;
        this.serverClock = serverClock;
        this.refreshSessionTtl = Duration.parse(refreshSessionTtl);
    }

    @Transactional
    public RefreshResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        String hash = refreshTokenHasher.hash(rawRefreshToken);
        // 12번 §2.2 2단계 — FOR UPDATE로 잠근다. 동시 재발급 요청 중 한쪽만 성공시키는 근거다.
        AuthSession session = authSessionRepository.findWithLockByRefreshTokenHash(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));

        LocalDateTime now = serverClock.now();
        if (!session.isUsable(now)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        Member member = memberRepository.findById(session.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));

        String newRawToken = refreshTokenHasher.generateToken();
        LocalDateTime newExpiresAt = now.plus(refreshSessionTtl);
        // 행 락을 이미 획득했으므로(위 FOR UPDATE) 더티 체킹으로 갱신해도 경합이 없다.
        session.rotate(refreshTokenHasher.hash(newRawToken), now, newExpiresAt);

        JwtAccessTokenIssuer.IssuedAccessToken issued =
                tokenIssuer.issue(member.getId(), member.getRole(), session.getId(), now);

        return new RefreshResult(issued.token(), issued.expiresAt(), newRawToken, newExpiresAt);
    }

    public record RefreshResult(
            String accessToken,
            LocalDateTime accessTokenExpiresAt,
            String rawRefreshToken,
            LocalDateTime refreshTokenExpiresAt) {
    }
}
