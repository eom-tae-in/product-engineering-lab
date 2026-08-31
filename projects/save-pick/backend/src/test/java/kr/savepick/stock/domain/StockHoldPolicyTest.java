package kr.savepick.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BR-006, BR-027 (docs/16-test-plan.md TC-020 잔여 수량 산정).
 */
class StockHoldPolicyTest {

    @Test
    @DisplayName("TC_020_판매_가능_수량_이내면_선점_가능하다")
    void TC_020_판매_가능_수량_이내면_선점_가능하다() {
        StockQuantities quantities = new StockQuantities(10, 3, 2, 0);
        assertThat(quantities.available()).isEqualTo(5);
        assertThat(StockHoldPolicy.canHold(quantities, 5)).isTrue();
        assertThat(StockHoldPolicy.canHold(quantities, 6)).isFalse();
    }

    @Test
    @DisplayName("부족分만큼_shortage를_계산한다")
    void 부족분만큼_shortage를_계산한다() {
        StockQuantities quantities = new StockQuantities(10, 3, 2, 0);
        assertThat(StockHoldPolicy.shortage(quantities, 5)).isZero();
        assertThat(StockHoldPolicy.shortage(quantities, 8)).isEqualTo(3);
    }

    @Test
    @DisplayName("마지막_1개는_1개_요청만_선점_가능하다")
    void 마지막_1개는_1개_요청만_선점_가능하다() {
        StockQuantities quantities = new StockQuantities(1, 0, 0, 0);
        assertThat(StockHoldPolicy.canHold(quantities, 1)).isTrue();
        assertThat(StockHoldPolicy.canHold(quantities, 2)).isFalse();
    }
}
