package kr.savepick.account.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import kr.savepick.account.domain.LoginAttemptRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BATCH-06이 호출하는 account 도메인의 정리 서비스 — 보관 기간이 지난 로그인 시도 기록 삭제
 * (11-api-spec.md §11, 10-erd.md §8 "90일 경과 행 삭제", FR-002, BR-002).
 *
 * <p>로그인 차단 판정(12-auth.md §1.2)은 최근 5건만 보므로 90일보다 오래된 기록은 판정에
 * 영향을 주지 않는다 — 보관 기간은 판정이 아니라 이상 로그인 조사용 보존 정책이다.
 */
@Service
public class LoginAttemptCleanupService {

    private final LoginAttemptRepository loginAttemptRepository;
    private final Duration retention;

    public LoginAttemptCleanupService(
            LoginAttemptRepository loginAttemptRepository,
            @Value("${savepick.retention.login-attempt}") String retention) {
        this.loginAttemptRepository = loginAttemptRepository;
        this.retention = Duration.parse(retention);
    }

    /**
     * 한 번 호출에 최대 {@code chunkSize}건을 지우고 그 자체로 트랜잭션 하나가 된다
     * (13번 §7.2).
     *
     * @return 이 호출에서 실제로 삭제한 행 수
     */
    @Transactional
    public int deleteOldAttempts(LocalDateTime now, int chunkSize) {
        LocalDateTime threshold = now.minus(retention);
        List<Long> ids = loginAttemptRepository.findIdsAttemptedBefore(threshold, chunkSize);
        if (ids.isEmpty()) {
            return 0;
        }
        return loginAttemptRepository.deleteByIdIn(ids);
    }
}
