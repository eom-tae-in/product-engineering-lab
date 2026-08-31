package kr.savepick.account.application;

import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-005 내 정보 조회, API-006 내 정보 수정 (FR-003). 이메일은 수정하지 않는다.
 */
@Service
public class MemberProfileService {

    private final MemberRepository memberRepository;
    private final ServerClock serverClock;

    public MemberProfileService(MemberRepository memberRepository, ServerClock serverClock) {
        this.memberRepository = memberRepository;
        this.serverClock = serverClock;
    }

    @Transactional(readOnly = true)
    public Member getProfile(Long memberId) {
        return findMember(memberId);
    }

    @Transactional
    public Member updateProfile(Long memberId, String name, String phone) {
        Member member = findMember(memberId);
        member.updateProfile(name, phone, serverClock.now());
        return memberRepository.save(member);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }
}
