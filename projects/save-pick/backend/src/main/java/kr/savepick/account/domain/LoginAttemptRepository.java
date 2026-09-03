package kr.savepick.account.domain;

import java.time.LocalDateTime;
import java.util.List;

public interface LoginAttemptRepository {
    LoginAttempt save(LoginAttempt attempt);

    /** attemptedAt 내림차순 최근 n건 (n = 12-auth.md §1.2의 차단 임계값). */
    List<LoginAttempt> findTop5ByEmailOrderByAttemptedAtDesc(String email);

    /** BATCH-06 — 보관 기간이 지난 로그인 시도 기록 (10-erd.md §8: 90일 경과 행 삭제). */
    List<Long> findIdsAttemptedBefore(LocalDateTime threshold, int limit);

    /** @return 실제로 삭제한 행 수 */
    int deleteByIdIn(List<Long> ids);
}
