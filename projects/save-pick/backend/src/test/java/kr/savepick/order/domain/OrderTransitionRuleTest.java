package kr.savepick.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 05-state-rules.md §2.2(허용 전이)·§2.3(허용하지 않는 전이 — 명시). DB 없이 정책만 검증한다
 * (14-project-structure.md §10 domain/).
 */
class OrderTransitionRuleTest {

    @Test
    @DisplayName("주문서_포기는_PENDING에서_EXPIRED로_전이한다")
    void 주문서_포기는_PENDING에서_EXPIRED로_전이한다() {
        assertThat(OrderTransitionRule.next(OrderStatus.PENDING, OrderEvent.ABANDON)).contains(OrderStatus.EXPIRED);
    }

    @Test
    @DisplayName("선점_만료는_PENDING에서_EXPIRED로_전이한다")
    void 선점_만료는_PENDING에서_EXPIRED로_전이한다() {
        assertThat(OrderTransitionRule.next(OrderStatus.PENDING, OrderEvent.HOLD_EXPIRE)).contains(OrderStatus.EXPIRED);
    }

    @Test
    @DisplayName("결제_성공은_PENDING에서_CONFIRMED로_전이한다")
    void 결제_성공은_PENDING에서_CONFIRMED로_전이한다() {
        assertThat(OrderTransitionRule.next(OrderStatus.PENDING, OrderEvent.PAYMENT_SUCCESS)).contains(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("결제_1_2회_실패는_PENDING을_유지한다")
    void 결제_1_2회_실패는_PENDING을_유지한다() {
        assertThat(OrderTransitionRule.next(OrderStatus.PENDING, OrderEvent.PAYMENT_FAIL_RETRY)).contains(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("결제_3회째_실패는_FAILED로_전이한다")
    void 결제_3회째_실패는_FAILED로_전이한다() {
        assertThat(OrderTransitionRule.next(OrderStatus.PENDING, OrderEvent.PAYMENT_FAIL_FINAL)).contains(OrderStatus.FAILED);
    }

    @Test
    @DisplayName("CONFIRMED와_READY는_취소_노쇼_완료로_전이할_수_있다")
    void CONFIRMED와_READY는_취소_노쇼_완료로_전이할_수_있다() {
        assertThat(OrderTransitionRule.next(OrderStatus.CONFIRMED, OrderEvent.MARK_READY)).contains(OrderStatus.READY);
        assertThat(OrderTransitionRule.next(OrderStatus.CONFIRMED, OrderEvent.COMPLETE)).contains(OrderStatus.COMPLETED);
        assertThat(OrderTransitionRule.next(OrderStatus.CONFIRMED, OrderEvent.CUSTOMER_CANCEL)).contains(OrderStatus.CANCELED);
        assertThat(OrderTransitionRule.next(OrderStatus.CONFIRMED, OrderEvent.ADMIN_CANCEL)).contains(OrderStatus.CANCELED);
        assertThat(OrderTransitionRule.next(OrderStatus.CONFIRMED, OrderEvent.NO_SHOW)).contains(OrderStatus.NO_SHOW);
        assertThat(OrderTransitionRule.next(OrderStatus.READY, OrderEvent.COMPLETE)).contains(OrderStatus.COMPLETED);
        assertThat(OrderTransitionRule.next(OrderStatus.READY, OrderEvent.CUSTOMER_CANCEL)).contains(OrderStatus.CANCELED);
        assertThat(OrderTransitionRule.next(OrderStatus.READY, OrderEvent.ADMIN_CANCEL)).contains(OrderStatus.CANCELED);
        assertThat(OrderTransitionRule.next(OrderStatus.READY, OrderEvent.NO_SHOW)).contains(OrderStatus.NO_SHOW);
    }

    @ParameterizedTest(name = "{0} 상태에서 {1} 이벤트는 거부한다(05 §2.3)")
    @MethodSource("disallowedTransitions")
    @DisplayName("05번_문서_2_3의_금지된_전이는_모두_거부한다")
    void 금지된_전이는_거부한다(OrderStatus from, OrderEvent event) {
        assertThat(OrderTransitionRule.isAllowed(from, event)).isFalse();
        assertThat(OrderTransitionRule.next(from, event)).isEmpty();
    }

    static Stream<Arguments> disallowedTransitions() {
        return Stream.of(
                // EXPIRED → PENDING, FAILED → PENDING/CONFIRMED : 최종 상태에서는 어떤 이벤트도 허용하지 않는다
                Arguments.of(OrderStatus.EXPIRED, OrderEvent.PAYMENT_SUCCESS),
                Arguments.of(OrderStatus.FAILED, OrderEvent.PAYMENT_SUCCESS),
                Arguments.of(OrderStatus.FAILED, OrderEvent.PAYMENT_FAIL_RETRY),
                // COMPLETED, CANCELED, NO_SHOW → 어떤 전이도 거부
                Arguments.of(OrderStatus.COMPLETED, OrderEvent.CUSTOMER_CANCEL),
                Arguments.of(OrderStatus.CANCELED, OrderEvent.CUSTOMER_CANCEL),
                Arguments.of(OrderStatus.NO_SHOW, OrderEvent.COMPLETE),
                Arguments.of(OrderStatus.NO_SHOW, OrderEvent.CUSTOMER_CANCEL),
                // CONFIRMED → PENDING(전이 자체가 없음), READY → CONFIRMED(전이 자체가 없음)
                Arguments.of(OrderStatus.CONFIRMED, OrderEvent.ABANDON),
                Arguments.of(OrderStatus.READY, OrderEvent.PAYMENT_SUCCESS),
                // PENDING → COMPLETED, PENDING → CANCELED(취소는 EXPIRED로만), PENDING → NO_SHOW
                Arguments.of(OrderStatus.PENDING, OrderEvent.COMPLETE),
                Arguments.of(OrderStatus.PENDING, OrderEvent.CUSTOMER_CANCEL),
                Arguments.of(OrderStatus.PENDING, OrderEvent.ADMIN_CANCEL),
                Arguments.of(OrderStatus.PENDING, OrderEvent.NO_SHOW));
    }
}
