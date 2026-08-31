package kr.savepick.account.infrastructure;

import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MemberJpaRepository extends JpaRepository<Member, Long>, MemberRepository {
    @Override
    Optional<Member> findByEmail(String email);

    @Override
    boolean existsByEmail(String email);
}
