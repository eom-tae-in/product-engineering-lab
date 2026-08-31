package kr.savepick.account.infrastructure;

import kr.savepick.account.domain.LoginAttempt;
import kr.savepick.account.domain.LoginAttemptRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LoginAttemptJpaRepository extends JpaRepository<LoginAttempt, Long>, LoginAttemptRepository {
    @Override
    List<LoginAttempt> findTop5ByEmailOrderByAttemptedAtDesc(String email);
}
