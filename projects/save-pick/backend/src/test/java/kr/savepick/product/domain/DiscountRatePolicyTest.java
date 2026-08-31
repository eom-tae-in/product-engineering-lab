package kr.savepick.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BR-004 (docs/16-test-plan.md TC-018 할인 구간 경계값 자동 계산).
 */
class DiscountRatePolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 28, 12, 0, 0);

    @Test
    @DisplayName("24시간_초과_남으면_할인율_0퍼센트다")
    void 이십사시간_초과_남으면_할인율_0퍼센트다() {
        LocalDateTime closingAt = NOW.plusHours(24).plusMinutes(1);
        assertThat(DiscountRatePolicy.discountRate(closingAt, NOW)).isZero();
    }

    @Test
    @DisplayName("TC_018_경계값_24시간이면_D1_30퍼센트다")
    void TC_018_경계값_24시간이면_D1_30퍼센트다() {
        LocalDateTime closingAt = NOW.plusHours(24);
        assertThat(DiscountRatePolicy.discountRate(closingAt, NOW)).isEqualTo(30);
    }

    @Test
    @DisplayName("TC_018_경계값_6시간이면_D2_50퍼센트다")
    void TC_018_경계값_6시간이면_D2_50퍼센트다() {
        LocalDateTime closingAt = NOW.plusHours(6);
        assertThat(DiscountRatePolicy.discountRate(closingAt, NOW)).isEqualTo(50);
    }

    @Test
    @DisplayName("TC_018_경계값_2시간이면_D3_70퍼센트다")
    void TC_018_경계값_2시간이면_D3_70퍼센트다() {
        LocalDateTime closingAt = NOW.plusHours(2);
        assertThat(DiscountRatePolicy.discountRate(closingAt, NOW)).isEqualTo(70);
    }

    @Test
    @DisplayName("마감_직전에는_70퍼센트다")
    void 마감_직전에는_칠십퍼센트다() {
        LocalDateTime closingAt = NOW.plusMinutes(1);
        assertThat(DiscountRatePolicy.discountRate(closingAt, NOW)).isEqualTo(70);
    }

    @Test
    @DisplayName("마감_시각에_도달하면_isClosed가_true다")
    void 마감_시각에_도달하면_isClosed가_true다() {
        assertThat(DiscountRatePolicy.isClosed(NOW, NOW)).isTrue();
        assertThat(DiscountRatePolicy.isClosed(NOW.minusSeconds(1), NOW)).isTrue();
        assertThat(DiscountRatePolicy.isClosed(NOW.plusSeconds(1), NOW)).isFalse();
    }

    @Test
    @DisplayName("마감된_상품의_할인율을_계산하려_하면_예외다")
    void 마감된_상품의_할인율을_계산하려_하면_예외다() {
        assertThatThrownBy(() -> DiscountRatePolicy.discountRate(NOW, NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("할인가는_10원_단위로_내림하고_100원_미만이면_100원이다")
    void 할인가는_십원_단위로_내림하고_백원_미만이면_백원이다() {
        assertThat(DiscountRatePolicy.discountPrice(12000, 0)).isEqualTo(12000);
        assertThat(DiscountRatePolicy.discountPrice(12000, 30)).isEqualTo(8400);
        assertThat(DiscountRatePolicy.discountPrice(12000, 50)).isEqualTo(6000);
        assertThat(DiscountRatePolicy.discountPrice(1005, 30)).isEqualTo(700);
        assertThat(DiscountRatePolicy.discountPrice(105, 30)).isEqualTo(100);
        assertThat(DiscountRatePolicy.discountPrice(100, 70)).isEqualTo(100);
    }

    @Test
    @DisplayName("다음_할인_구간과_시각을_계산한다")
    void 다음_할인_구간과_시각을_계산한다() {
        LocalDateTime closingAt = NOW.plusHours(30);
        var next = DiscountRatePolicy.nextDiscount(closingAt, NOW).orElseThrow();
        assertThat(next.rate()).isEqualTo(30);
        assertThat(next.at()).isEqualTo(closingAt.minusHours(24));
    }

    @Test
    @DisplayName("이미_최대_할인_구간이면_다음_구간이_없다")
    void 이미_최대_할인_구간이면_다음_구간이_없다() {
        LocalDateTime closingAt = NOW.plusHours(1);
        assertThat(DiscountRatePolicy.nextDiscount(closingAt, NOW)).isEmpty();
    }
}
