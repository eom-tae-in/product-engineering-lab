package kr.savepick.account.application;

import java.util.UUID;
import kr.savepick.account.infrastructure.AuthSessionJpaRepository;
import kr.savepick.common.time.ServerClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-004 로그아웃 (12-auth.md §2.4) — 해당 세션 1건만 폐기한다.
 */
@Service
public class LogoutService {

    private final AuthSessionJpaRepository authSessionRepository;
    private final ServerClock serverClock;

    public LogoutService(AuthSessionJpaRepository authSessionRepository, ServerClock serverClock) {
        this.authSessionRepository = authSessionRepository;
        this.serverClock = serverClock;
    }

    @Transactional
    public void logout(UUID sessionId) {
        // 이 메서드에 도달했다는 것은 JWT 인증 필터를 이미 통과했다는 뜻이다.
        // 세션이 이미 폐기·삭제됐어도 재요청은 멱등하게 204로 처리한다.
        authSessionRepository.findById(sessionId)
                .ifPresent(session -> session.revoke(serverClock.now()));
    }
}
