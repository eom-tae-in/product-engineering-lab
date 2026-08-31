package kr.savepick.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductStatus;
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
 * API-103 (11-api-spec.md §7, BR-003, BR-009, docs/16-test-plan.md TC-071·TC-072).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class ProductRegisterServiceIntegrationTest {

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private ServerClock serverClock;

    @Test
    @DisplayName("TC_071_등록_직후_DRAFT_상태다")
    void TC_071_등록_직후_DRAFT_상태다() {
        LocalDateTime now = serverClock.now();
        Product product = productRegisterService.register(
                "국내산 삼겹살 300g", "오늘 손질한 국내산 삼겹살입니다.", "300g", 12000,
                ProductTestFixtures.futureClosingAt(now, 3), (short) 5, 1L);

        assertThat(product.getId()).isNotNull();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DRAFT);
        assertThat(product.getOriginalPrice()).isEqualTo(12000);
    }

    @Test
    @DisplayName("TC_072a_마감_시각이_과거이면_CLOSING_TIME_INVALID다")
    void TC_072a_마감_시각이_과거이면_CLOSING_TIME_INVALID다() {
        LocalDateTime now = serverClock.now();
        assertThatThrownBy(() -> productRegisterService.register(
                        "상품", "설명", "1개", 1000, now.minusMinutes(1), (short) 5, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CLOSING_TIME_INVALID);
    }

    @Test
    @DisplayName("TC_072b_마감_시각이_영업_종료_시각을_넘으면_CLOSING_TIME_INVALID다")
    void TC_072b_마감_시각이_영업_종료_시각을_넘으면_CLOSING_TIME_INVALID다() {
        LocalDateTime now = serverClock.now();
        LocalDateTime pastCloseTime = now.toLocalDate().atTime(23, 0);
        assertThatThrownBy(() -> productRegisterService.register(
                        "상품", "설명", "1개", 1000, pastCloseTime, (short) 5, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CLOSING_TIME_INVALID);
    }
}
