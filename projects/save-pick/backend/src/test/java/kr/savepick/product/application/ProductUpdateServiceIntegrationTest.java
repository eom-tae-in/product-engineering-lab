package kr.savepick.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.domain.Product;
import kr.savepick.product.infrastructure.ProductChangeLogJpaRepository;
import kr.savepick.support.ProductTestFixtures;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-105 (11-api-spec.md §7, BR-003, BR-005, docs/16-test-plan.md TC-073·TC-074).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class ProductUpdateServiceIntegrationTest {

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private ProductUpdateService productUpdateService;

    @Autowired
    private ProductChangeLogJpaRepository productChangeLogJpaRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ServerClock serverClock;

    private Product registerSample() {
        LocalDateTime now = serverClock.now();
        return productRegisterService.register("상품", "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, 1L);
    }

    private Long registerAdmin() {
        Member admin = Member.registerAdmin(
                "admin-" + java.util.UUID.randomUUID() + "@test.com", "hash", "관리자", "01000000000", serverClock.now());
        return memberRepository.save(admin).getId();
    }

    @Test
    @DisplayName("TC_073_정가를_수정하면_반영되고_이력이_남는다")
    void TC_073_정가를_수정하면_반영되고_이력이_남는다() {
        Product product = registerSample();
        Long adminId = registerAdmin();

        ProductUpdateService.UpdateResult result =
                productUpdateService.update(product.getId(), null, null, null, 11000, null, null, false, adminId);

        assertThat(result.product().getOriginalPrice()).isEqualTo(11000);
        assertThat(result.changedFields()).containsExactly("originalPrice");
        var logs = productChangeLogJpaRepository.findByProductIdOrderByChangedAtDesc(product.getId(), PageRequest.of(0, 10));
        assertThat(logs.getContent()).anySatisfy(log -> {
            assertThat(log.getChangedField()).isEqualTo("original_price");
            assertThat(log.getBeforeValue()).isEqualTo("10000");
            assertThat(log.getAfterValue()).isEqualTo("11000");
        });
    }

    @Test
    @DisplayName("TC_074_마감_시각을_단축해도_원장에_남고_저장된다")
    void TC_074_마감_시각을_단축해도_원장에_남고_저장된다() {
        Product product = registerSample();
        Long adminId = registerAdmin();
        LocalDateTime shortened = product.getClosingAt().minusHours(1);

        ProductUpdateService.UpdateResult result =
                productUpdateService.update(product.getId(), null, null, null, null, shortened, null, false, adminId);

        assertThat(result.product().getClosingAt()).isEqualTo(shortened);
        assertThat(result.affectedConfirmedOrderCount()).isZero();
    }

    @Test
    @DisplayName("마감_시각을_영업_종료_이후로_옮기면_CLOSING_TIME_INVALID다")
    void 마감_시각을_영업_종료_이후로_옮기면_CLOSING_TIME_INVALID다() {
        Product product = registerSample();
        LocalDateTime invalid = serverClock.now().toLocalDate().atTime(23, 0);

        assertThatThrownBy(() -> productUpdateService.update(product.getId(), null, null, null, null, invalid, null, false, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CLOSING_TIME_INVALID);
    }

    @Test
    @DisplayName("존재하지_않는_상품을_수정하면_NOT_FOUND다")
    void 존재하지_않는_상품을_수정하면_NOT_FOUND다() {
        assertThatThrownBy(() -> productUpdateService.update(999_999L, "새 이름", null, null, null, null, null, false, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
