package kr.savepick.account.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.savepick.account.domain.AuthSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * AuthSession은 재발급 시 행 락(FOR UPDATE)이 필요해 application이 이 infra 타입을
 * 직접 사용한다 (14-project-structure.md §5 ProductStockJpaRepository#findForUpdate와 같은 예외).
 */
public interface AuthSessionJpaRepository extends JpaRepository<AuthSession, UUID> {

    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthSession> findWithLockByRefreshTokenHash(String refreshTokenHash);

    /** BATCH-06 — 보관 기간이 지난 만료 세션 (10-erd.md §8: {@code expires_at < now() - 7일}). */
    @Query("select s.id from AuthSession s where s.expiresAt < :threshold order by s.expiresAt asc")
    List<UUID> findIdsExpiredBefore(@Param("threshold") LocalDateTime threshold, Pageable limit);

    default List<UUID> findIdsExpiredBefore(LocalDateTime threshold, int limit) {
        return findIdsExpiredBefore(threshold, PageRequest.of(0, limit));
    }

    @Modifying(clearAutomatically = true)
    @Query("delete from AuthSession s where s.id in :ids")
    int deleteByIdIn(@Param("ids") List<UUID> ids);
}
