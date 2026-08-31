package kr.savepick.account.domain;

import java.util.Optional;

public interface MemberRestrictionRepository {
    MemberRestriction save(MemberRestriction restriction);

    Optional<MemberRestriction> findTopByMemberIdOrderByEndsAtDesc(Long memberId);
}
