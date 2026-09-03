package kr.savepick.account.application;

import static org.assertj.core.api.Assertions.assertThat;

import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.account.infrastructure.BcryptPasswordHasher;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 12-auth.md §5 P7 — 운영에서 첫 관리자 계정을 만드는 유일한 경로. */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class AdminAccountBootstrapServiceIntegrationTest {

    @Autowired
    private AdminAccountBootstrapService bootstrapService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BcryptPasswordHasher passwordHasher;

    @Test
    @DisplayName("계정이_없으면_ADMIN_역할로_만든다")
    void 계정이_없으면_ADMIN_역할로_만든다() {
        var result = bootstrapService.createIfAbsent("boot-admin@test.com", "adminpass1", "매장 관리자", "01099998888");

        assertThat(result).isEqualTo(AdminAccountBootstrapService.Result.CREATED);
        Member created = memberRepository.findByEmail("boot-admin@test.com").orElseThrow();
        assertThat(created.getRole().name()).isEqualTo("ADMIN");
        assertThat(created.getName()).isEqualTo("매장 관리자");
        assertThat(passwordHasher.matches("adminpass1", created.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("이메일_대소문자가_달라도_같은_계정으로_본다")
    void 이메일_대소문자가_달라도_같은_계정으로_본다() {
        bootstrapService.createIfAbsent("Boot-Case@test.com", "adminpass1", "매장 관리자", "01099998888");

        var result = bootstrapService.createIfAbsent("boot-case@test.com", "adminpass1", "매장 관리자", "01099998888");

        assertThat(result).isEqualTo(AdminAccountBootstrapService.Result.ALREADY_EXISTS);
    }

    @Test
    @DisplayName("이미_있으면_비밀번호를_덮어쓰지_않는다")
    void 이미_있으면_비밀번호를_덮어쓰지_않는다() {
        bootstrapService.createIfAbsent("boot-keep@test.com", "originalpass1", "매장 관리자", "01099998888");

        var result = bootstrapService.createIfAbsent("boot-keep@test.com", "changedpass9", "다른 이름", "01000001111");

        assertThat(result).isEqualTo(AdminAccountBootstrapService.Result.ALREADY_EXISTS);
        Member kept = memberRepository.findByEmail("boot-keep@test.com").orElseThrow();
        assertThat(passwordHasher.matches("originalpass1", kept.getPasswordHash())).isTrue();
        assertThat(kept.getName()).isEqualTo("매장 관리자");
    }

    @Test
    @DisplayName("이미_있는_계정이_고객이면_관리자로_승격하지_않는다")
    void 이미_있는_계정이_고객이면_관리자로_승격하지_않는다() {
        memberRepository.save(Member.registerCustomer(
                "boot-customer@test.com", passwordHasher.hash("custpass12"), "김지현", "01011112222",
                java.time.LocalDateTime.now()));

        var result = bootstrapService.createIfAbsent("boot-customer@test.com", "adminpass1", "매장 관리자", "01099998888");

        assertThat(result).isEqualTo(AdminAccountBootstrapService.Result.ALREADY_EXISTS);
        assertThat(memberRepository.findByEmail("boot-customer@test.com").orElseThrow().getRole().name())
                .isEqualTo("CUSTOMER");
    }
}
