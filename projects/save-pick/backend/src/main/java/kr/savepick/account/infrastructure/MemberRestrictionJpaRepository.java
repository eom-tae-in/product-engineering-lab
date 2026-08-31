package kr.savepick.account.infrastructure;

import kr.savepick.account.domain.MemberRestriction;
import kr.savepick.account.domain.MemberRestrictionRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MemberRestrictionJpaRepository extends JpaRepository<MemberRestriction, Long>, MemberRestrictionRepository {
    @Override
    Optional<MemberRestriction> findTopByMemberIdOrderByEndsAtDesc(Long memberId);
}

