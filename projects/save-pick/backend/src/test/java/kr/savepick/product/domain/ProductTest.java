package kr.savepick.product.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 05-state-rules.md §5.2·5.3 상품 상태 전이 (docs/16-test-plan.md TC-071, TC-075).
 */
class ProductTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 28, 12, 0, 0);

    private Product register() {
        return Product.register((short) 1, "상품", "설명", "1개", 1000, NOW.plusHours(3), (short) 5, NOW);
    }

    @Test
    @DisplayName("TC_071_등록_직후_DRAFT다")
    void TC_071_등록_직후_DRAFT다() {
        assertThat(register().getStatus()).isEqualTo(ProductStatus.DRAFT);
    }

    @Test
    @DisplayName("TC_075a_재고_미등록_DRAFT는_ON_SALE로_전환할_수_없다")
    void TC_075a_재고_미등록_DRAFT는_ON_SALE로_전환할_수_없다() {
        Product product = register();
        assertThat(product.canTransitionTo(ProductStatus.ON_SALE, false)).isFalse();
    }

    @Test
    @DisplayName("재고가_등록된_DRAFT는_ON_SALE로_전환할_수_있다")
    void 재고가_등록된_DRAFT는_ON_SALE로_전환할_수_있다() {
        Product product = register();
        assertThat(product.canTransitionTo(ProductStatus.ON_SALE, true)).isTrue();
        product.startSale(NOW);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("ON_SALE에서_DRAFT로는_전환할_수_없다")
    void ON_SALE에서_DRAFT로는_전환할_수_없다() {
        Product product = register();
        product.startSale(NOW);
        assertThat(product.canTransitionTo(ProductStatus.DRAFT, true)).isFalse();
    }

    @Test
    @DisplayName("ON_SALE과_HIDDEN은_서로_전환할_수_있다")
    void ON_SALE과_HIDDEN은_서로_전환할_수_있다() {
        Product product = register();
        product.startSale(NOW);
        assertThat(product.canTransitionTo(ProductStatus.HIDDEN, true)).isTrue();
        product.hide(NOW);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
        assertThat(product.canTransitionTo(ProductStatus.ON_SALE, true)).isTrue();
    }

    @Test
    @DisplayName("TC_075b_CLOSED_상품은_ON_SALE로_전환할_수_없다")
    void TC_075b_CLOSED_상품은_ON_SALE로_전환할_수_없다() {
        Product product = Product.register((short) 1, "상품", "설명", "1개", 1000, NOW.plusMinutes(1), (short) 5, NOW);
        assertThat(product.closeIfDue(NOW.plusMinutes(2))).isTrue();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.CLOSED);
        assertThat(product.canTransitionTo(ProductStatus.ON_SALE, true)).isFalse();
        assertThat(product.canTransitionTo(ProductStatus.HIDDEN, true)).isFalse();
    }

    @Test
    @DisplayName("CLOSED_상품의_마감_시각은_수정할_수_없다")
    void CLOSED_상품의_마감_시각은_수정할_수_없다() {
        Product product = Product.register((short) 1, "상품", "설명", "1개", 1000, NOW.plusMinutes(1), (short) 5, NOW);
        product.closeIfDue(NOW.plusMinutes(2));
        assertThat(product.isClosingAtEditable()).isFalse();
    }

    @Test
    @DisplayName("마감_시각_이전에는_closeIfDue가_아무_일도_하지_않는다")
    void 마감_시각_이전에는_closeIfDue가_아무_일도_하지_않는다() {
        Product product = register();
        assertThat(product.closeIfDue(NOW)).isFalse();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DRAFT);
    }

    @Test
    @DisplayName("API_106_대상이_아닌_CLOSED로의_전환은_어떤_상태에서도_거부된다")
    void API_106_대상이_아닌_CLOSED로의_전환은_어떤_상태에서도_거부된다() {
        Product product = register();
        assertThat(product.canTransitionTo(ProductStatus.CLOSED, true)).isFalse();
    }
}
