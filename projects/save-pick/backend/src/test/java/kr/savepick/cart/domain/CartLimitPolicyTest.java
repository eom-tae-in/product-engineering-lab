package kr.savepick.cart.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BR-009 (docs/16-test-plan.md TC-021 장바구니 담기 한도).
 */
class CartLimitPolicyTest {

    @Test
    @DisplayName("TC_021_품목당_수량은_최대_주문_수량을_넘을_수_없다")
    void TC_021_품목당_수량은_최대_주문_수량을_넘을_수_없다() {
        assertThat(CartLimitPolicy.isWithinMaxOrderQuantity(5, (short) 5)).isTrue();
        assertThat(CartLimitPolicy.isWithinMaxOrderQuantity(6, (short) 5)).isFalse();
    }

    @Test
    @DisplayName("TC_021_품목_수는_10개를_넘을_수_없다")
    void TC_021_품목_수는_10개를_넘을_수_없다() {
        assertThat(CartLimitPolicy.isWithinItemLimit(10)).isTrue();
        assertThat(CartLimitPolicy.isWithinItemLimit(11)).isFalse();
    }

    @Test
    @DisplayName("최대_품목_수_상수는_10이다")
    void 최대_품목_수_상수는_10이다() {
        assertThat(CartLimitPolicy.MAX_ITEM_COUNT).isEqualTo(10);
    }
}
