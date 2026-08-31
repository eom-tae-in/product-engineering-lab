package kr.savepick.order.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** BR-012 순수 정책 단위 테스트(DB 없음). */
class PaymentRetryPolicyTest {

    @Test
    @DisplayName("시도_번호가_한도와_같으면_마지막_시도다")
    void 시도_번호가_한도와_같으면_마지막_시도다() {
        assertThat(PaymentRetryPolicy.isFinalAttempt(3, 3)).isTrue();
        assertThat(PaymentRetryPolicy.isFinalAttempt(2, 3)).isFalse();
        assertThat(PaymentRetryPolicy.isFinalAttempt(1, 3)).isFalse();
    }

    @Test
    @DisplayName("다음_시도_번호가_한도를_넘으면_거부한다")
    void 다음_시도_번호가_한도를_넘으면_거부한다() {
        assertThat(PaymentRetryPolicy.exceedsLimit(4, 3)).isTrue();
        assertThat(PaymentRetryPolicy.exceedsLimit(3, 3)).isFalse();
        assertThat(PaymentRetryPolicy.exceedsLimit(1, 3)).isFalse();
    }
}
