package kr.savepick.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BR-006 (docs/16-test-plan.md TC-020, 13번 §2.1 읽기 보정).
 */
class StockQuantitiesTest {

    @Test
    @DisplayName("판매_가능_수량은_총재고에서_선점과_확정을_뺀_값이다")
    void 판매_가능_수량은_총재고에서_선점과_확정을_뺀_값이다() {
        StockQuantities quantities = new StockQuantities(10, 3, 2, 0);
        assertThat(quantities.available()).isEqualTo(5);
    }

    @Test
    @DisplayName("만료된_선점을_제외하면_판매_가능_수량이_늘어나고_선점_수량이_줄어든다")
    void 만료된_선점을_제외하면_판매_가능_수량이_늘어나고_선점_수량이_줄어든다() {
        StockQuantities raw = new StockQuantities(10, 5, 2, 0);
        StockQuantities corrected = raw.withExpiredHeldExcluded(3);

        assertThat(corrected.held()).isEqualTo(2);
        assertThat(corrected.available()).isEqualTo(6);
        assertThat(corrected.total()).isEqualTo(raw.total());
        assertThat(corrected.confirmed()).isEqualTo(raw.confirmed());
    }

    @Test
    @DisplayName("만료된_선점이_없으면_보정하지_않는다")
    void 만료된_선점이_없으면_보정하지_않는다() {
        StockQuantities raw = new StockQuantities(10, 5, 2, 0);
        assertThat(raw.withExpiredHeldExcluded(0)).isEqualTo(raw);
    }

    @Test
    @DisplayName("델타를_적용하면_각_값이_델타만큼_바뀐다")
    void 델타를_적용하면_각_값이_델타만큼_바뀐다() {
        StockQuantities before = new StockQuantities(10, 0, 0, 0);
        StockQuantities after = before.applyDelta(0, 3, 0, 0);
        assertThat(after.held()).isEqualTo(3);
        assertThat(after.available()).isEqualTo(7);
    }
}
