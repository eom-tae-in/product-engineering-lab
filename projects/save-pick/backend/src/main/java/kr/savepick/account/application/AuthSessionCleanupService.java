package kr.savepick.account.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import kr.savepick.account.infrastructure.AuthSessionJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BATCH-06이 호출하는 account 도메인의 정리 서비스 — 만료 세션 삭제
 * (11-api-spec.md §11, 10-erd.md §8, FR-002 인증 위생, 14-project-structure.md §6.2).
 *
 * <p>보관 기간은 10-erd.md §8의 {@code expires_at < now() - 7일}이다 — 만료 직후 바로 지우지
 * 않고 유예를 두는 이유는, 재발급 실패 원인(만료인지 폐기인지)을 조사할 여지를 남기기
 * 위해서다. 취소(revoked) 세션도 같은 기준을 따른다(만료 시각이 지나야 지운다).
 */
@Service
public class AuthSessionCleanupService {

    private final AuthSessionJpaRepository authSessionJpaRepository;
    private final Duration retention;

    public AuthSessionCleanupService(
            AuthSessionJpaRepository authSessionJpaRepository,
            @Value("${savepick.retention.expired-session}") String retention) {
        this.authSessionJpaRepository = authSessionJpaRepository;
        this.retention = Duration.parse(retention);
    }

    /**
     * 한 번 호출에 최대 {@code chunkSize}건을 지우고 그 자체로 트랜잭션 하나가 된다 — 배치가
     * 더 지울 것이 없을 때까지 반복 호출한다(13번 §7.2 — 많은 행을 한 트랜잭션에 묶지 않는다).
     *
     * @return 이 호출에서 실제로 삭제한 행 수
     */
    @Transactional
    public int deleteExpiredSessions(LocalDateTime now, int chunkSize) {
        LocalDateTime threshold = now.minus(retention);
        List<UUID> ids = authSessionJpaRepository.findIdsExpiredBefore(threshold, chunkSize);
        if (ids.isEmpty()) {
            return 0;
        }
        return authSessionJpaRepository.deleteByIdIn(ids);
    }
}
