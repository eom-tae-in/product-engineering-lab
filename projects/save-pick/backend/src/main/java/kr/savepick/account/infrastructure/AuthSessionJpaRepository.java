package kr.savepick.account.infrastructure;

import jakarta.persistence.LockModeType;
import kr.savepick.account.domain.AuthSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/**
 * AuthSession은 재발급 시 행 락(FOR UPDATE)이 필요해 application이 이 infra 타입을
 * 직접 사용한다 (14-project-structure.md §5 ProductStockJpaRepository#findForUpdate와 같은 예외).
 */
public interface AuthSessionJpaRepository extends JpaRepository<AuthSession, UUID> {

    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthSession> findWithLockByRefreshTokenHash(String refreshTokenHash);
}
