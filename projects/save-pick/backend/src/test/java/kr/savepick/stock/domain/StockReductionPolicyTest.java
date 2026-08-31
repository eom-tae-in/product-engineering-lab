package kr.savepick.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BR-025 (docs/16-test-plan.md TC-080 재고 축소 하한선).
 */
class StockReductionPolicyTest {

    @Test
    @DisplayName("TC_080_확정6_선점2면_최소설정값은_8이다")
    void TC_080_확정6_선점2면_최소설정값은_8이다() {
        StockQuantities quantities = new StockQuantities(20, 2, 6, 0);
        assertThat(StockReductionPolicy.minimumSettableQuantity(quantities)).isEqualTo(8);
        assertThat(StockReductionPolicy.isTargetAllowed(7, quantities)).isFalse();
        assertThat(StockReductionPolicy.isTargetAllowed(8, quantities)).isTrue();
    }

    @Test
    @DisplayName("선점_확정이_없으면_0까지_축소를_허용한다")
    void 선점_확정이_없으면_0까지_축소를_허용한다() {
        StockQuantities quantities = new StockQuantities(20, 0, 0, 0);
        assertThat(StockReductionPolicy.isTargetAllowed(0, quantities)).isTrue();
    }

    @Test
    @DisplayName("증가_조정은_항상_허용된다")
    void 증가_조정은_항상_허용된다() {
        StockQuantities quantities = new StockQuantities(20, 5, 10, 0);
        assertThat(StockReductionPolicy.isTargetAllowed(100, quantities)).isTrue();
    }
}
