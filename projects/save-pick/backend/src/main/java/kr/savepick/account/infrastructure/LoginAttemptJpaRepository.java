package kr.savepick.account.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import kr.savepick.account.domain.LoginAttempt;
import kr.savepick.account.domain.LoginAttemptRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginAttemptJpaRepository extends JpaRepository<LoginAttempt, Long>, LoginAttemptRepository {
    @Override
    List<LoginAttempt> findTop5ByEmailOrderByAttemptedAtDesc(String email);

    /** BATCH-06 대상 조회 — 한 번에 한 덩어리씩만 가져와 삭제 트랜잭션을 짧게 유지한다. */
    @Query("select a.id from LoginAttempt a where a.attemptedAt < :threshold order by a.attemptedAt asc")
    List<Long> findIdsAttemptedBefore(@Param("threshold") LocalDateTime threshold, Pageable limit);

    @Override
    default List<Long> findIdsAttemptedBefore(LocalDateTime threshold, int limit) {
        return findIdsAttemptedBefore(threshold, PageRequest.of(0, limit));
    }

    @Override
    @Modifying(clearAutomatically = true)
    @Query("delete from LoginAttempt a where a.id in :ids")
    int deleteByIdIn(@Param("ids") List<Long> ids);
}
