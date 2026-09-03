package kr.savepick.account.application;

import java.time.LocalDateTime;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.account.infrastructure.BcryptPasswordHasher;
import kr.savepick.common.time.ServerClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영 환경에서 첫 관리자 계정을 만든다 (12-auth.md §5 P7).
 *
 * <p>P7은 "관리자 계정은 API로 만들지 않는다 — 운영자가 DB 마이그레이션·운영 스크립트로
 * {@code role = 'ADMIN'}을 부여한다"고 정했다. 그런데 첫 버전에는 그 스크립트가 없었고
 * {@code dev} 프로파일 시드({@code DevDataSeeder})만이 유일한 생성 경로였다. 그대로
 * 배포하면 관리자 화면(SC-101~113)에 아무도 로그인할 수 없다.
 *
 * <p>이 서비스가 P7이 말한 "운영 스크립트"에 해당한다. 마이그레이션 SQL로 넣지 않는 이유는
 * 비밀번호 해시다 — bcrypt 해시를 SQL에 적으려면 그 값이 저장소에 남고, 모든 배포 환경이
 * 같은 비밀번호를 쓰게 된다. 기동 시점에 환경변수로 받아 해시하면 저장소에 남지 않는다.
 */
@Service
public class AdminAccountBootstrapService {

    public enum Result {
        /** 계정을 새로 만들었다. */
        CREATED,
        /** 같은 이메일이 이미 있어 아무것도 하지 않았다. */
        ALREADY_EXISTS
    }

    private final MemberRepository memberRepository;
    private final BcryptPasswordHasher passwordHasher;
    private final ServerClock serverClock;

    public AdminAccountBootstrapService(
            MemberRepository memberRepository,
            BcryptPasswordHasher passwordHasher,
            ServerClock serverClock) {
        this.memberRepository = memberRepository;
        this.passwordHasher = passwordHasher;
        this.serverClock = serverClock;
    }

    /**
     * 같은 이메일이 없을 때만 관리자 계정을 만든다.
     *
     * <p>이미 있으면 비밀번호를 덮어쓰지 않는다. 운영자가 환경변수를 지우지 않은 채 재기동해도
     * 계정이 원래대로 돌아가면 안 되기 때문이다 — 비밀번호를 바꾸는 것은 별도 운영 절차다
     * (12-auth.md §2.4).
     *
     * <p>이미 있는 계정이 고객({@code role = 'CUSTOMER'})이어도 관리자로 승격하지 않는다.
     * 승격은 이 경로가 책임질 범위가 아니고, 조용히 권한이 올라가는 것이 더 위험하다.
     */
    @Transactional
    public Result createIfAbsent(String rawEmail, String rawPassword, String name, String phone) {
        String email = rawEmail.toLowerCase();
        if (memberRepository.existsByEmail(email)) {
            return Result.ALREADY_EXISTS;
        }

        LocalDateTime now = serverClock.now();
        memberRepository.save(Member.registerAdmin(email, passwordHasher.hash(rawPassword), name, phone, now));
        return Result.CREATED;
    }
}
