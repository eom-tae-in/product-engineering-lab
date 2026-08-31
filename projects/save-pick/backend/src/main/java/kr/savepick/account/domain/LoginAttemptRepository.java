package kr.savepick.account.domain;

import java.util.List;

public interface LoginAttemptRepository {
    LoginAttempt save(LoginAttempt attempt);

    /** attemptedAt 내림차순 최근 n건 (n = 12-auth.md §1.2의 차단 임계값). */
    List<LoginAttempt> findTop5ByEmailOrderByAttemptedAtDesc(String email);
}
