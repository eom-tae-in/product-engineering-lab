package kr.savepick.account.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 12-auth.md §1.2 — 같은 이메일에 대해 마지막 성공 이후 연속 실패가 threshold건 이상이고,
 * threshold번째 실패 시각 + blockDuration이 아직 지나지 않았으면 차단한다.
 * 도메인 정책이므로 Clock을 직접 호출하지 않고 now를 인자로 받는다 (14-project-structure.md §9.1).
 */
public class LoginBlockPolicy {

    private final int threshold;
    private final Duration blockDuration;

    public LoginBlockPolicy(int threshold, Duration blockDuration) {
        this.threshold = threshold;
        this.blockDuration = blockDuration;
    }

    /**
     * @param recentAttemptsDesc 같은 이메일의 최근 시도, attemptedAt 내림차순, 최소 threshold건까지만 있으면 된다.
     */
    public BlockDecision evaluate(List<LoginAttempt> recentAttemptsDesc, LocalDateTime now) {
        if (recentAttemptsDesc.size() < threshold) {
            return BlockDecision.notBlocked();
        }
        boolean allRecentFailed = recentAttemptsDesc.stream()
                .limit(threshold)
                .noneMatch(LoginAttempt::isSucceeded);
        if (!allRecentFailed) {
            return BlockDecision.notBlocked();
        }
        LocalDateTime thresholdFailureAt = recentAttemptsDesc.get(threshold - 1).getAttemptedAt();
        LocalDateTime retryAfterAt = thresholdFailureAt.plus(blockDuration);
        if (now.isBefore(retryAfterAt)) {
            return BlockDecision.blocked(retryAfterAt);
        }
        return BlockDecision.notBlocked();
    }

    public record BlockDecision(boolean blocked, LocalDateTime retryAfterAt) {
        public static BlockDecision notBlocked() {
            return new BlockDecision(false, null);
        }

        public static BlockDecision blocked(LocalDateTime retryAfterAt) {
            return new BlockDecision(true, retryAfterAt);
        }
    }
}
