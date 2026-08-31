package kr.savepick.stock.infrastructure;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 13번 §3, §7.1 — {@code product_stocks} 행 락을 잡는 트랜잭션 진입 직후 {@code SET LOCAL lock_timeout}을
 * 적용한다. 락 보유 구간은 수 ms이므로, 설정값(기본 3초)을 넘게 기다렸다면 정상 경합이 아니라
 * 이상 상황이라고 판단해 대기 대신 실패시킨다 (savepick.stock.lock-timeout).
 */
@Component
public class LockTimeoutGuard {

    private final EntityManager entityManager;
    private final Duration lockTimeout;

    public LockTimeoutGuard(EntityManager entityManager, @Value("${savepick.stock.lock-timeout}") String lockTimeout) {
        this.entityManager = entityManager;
        this.lockTimeout = Duration.parse(lockTimeout);
    }

    public void apply() {
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '" + lockTimeout.toMillis() + "ms'").executeUpdate();
    }
}
