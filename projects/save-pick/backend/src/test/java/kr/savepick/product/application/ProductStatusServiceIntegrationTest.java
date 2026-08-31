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
import kr.savepick.product.domain.ProductRepository;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.stock.application.StockAdjustService;
import kr.savepick.support.ProductTestFixtures;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-106 (11-api-spec.md §7, BR-025, BR-030, docs/16-test-plan.md TC-075·TC-076).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class ProductStatusServiceIntegrationTest {

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductStatusService productStatusService;

    @Autowired
    private StockAdjustService stockAdjustService;

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
    @DisplayName("TC_075a_재고_미등록_DRAFT를_ON_SALE로_전환하면_PRODUCT_STATUS_TRANSITION_DENIED다")
    void TC_075a_재고_미등록_DRAFT를_ON_SALE로_전환하면_PRODUCT_STATUS_TRANSITION_DENIED다() {
        Product product = registerSample();
        Long adminId = registerAdmin();

        assertThatThrownBy(() -> productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_STATUS_TRANSITION_DENIED);
    }

    @Test
    @DisplayName("재고를_등록하면_DRAFT를_ON_SALE로_전환할_수_있다")
    void 재고를_등록하면_DRAFT를_ON_SALE로_전환할_수_있다() {
        Product product = registerSample();
        Long adminId = registerAdmin();
        stockAdjustService.adjust(product.getId(), 10, "초기 등록", adminId);

        ProductStatusService.StatusChangeResult result =
                productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);

        assertThat(result.product().getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("TC_075b_CLOSED_상품을_ON_SALE로_전환하면_PRODUCT_STATUS_TRANSITION_DENIED다")
    void TC_075b_CLOSED_상품을_ON_SALE로_전환하면_PRODUCT_STATUS_TRANSITION_DENIED다() {
        Product product = registerSample();
        Long adminId = registerAdmin();
        stockAdjustService.adjust(product.getId(), 10, null, adminId);
        productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);

        // BATCH-02(상품 마감 상태 전환)는 이번 슬라이스 범위 밖이라 시스템 전이를 직접 흉내낸다.
        Product managed = productRepository.findById(product.getId()).orElseThrow();
        assertThat(managed.closeIfDue(managed.getClosingAt().plusMinutes(1))).isTrue();
        productRepository.save(managed);

        assertThatThrownBy(() -> productStatusService.changeStatus(product.getId(), ProductStatus.HIDDEN, adminId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_STATUS_TRANSITION_DENIED);
    }

    @Test
    @DisplayName("TC_076_HIDDEN_전환_후_다시_ON_SALE로_전환할_수_있다")
    void TC_076_HIDDEN_전환_후_다시_ON_SALE로_전환할_수_있다() {
        Product product = registerSample();
        Long adminId = registerAdmin();
        stockAdjustService.adjust(product.getId(), 10, null, adminId);
        productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);

        ProductStatusService.StatusChangeResult hidden =
                productStatusService.changeStatus(product.getId(), ProductStatus.HIDDEN, adminId);
        assertThat(hidden.product().getStatus()).isEqualTo(ProductStatus.HIDDEN);

        ProductStatusService.StatusChangeResult resumed =
                productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);
        assertThat(resumed.product().getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("존재하지_않는_상품의_상태를_바꾸면_NOT_FOUND다")
    void 존재하지_않는_상품의_상태를_바꾸면_NOT_FOUND다() {
        Long adminId = registerAdmin();
        assertThatThrownBy(() -> productStatusService.changeStatus(999_999L, ProductStatus.ON_SALE, adminId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
