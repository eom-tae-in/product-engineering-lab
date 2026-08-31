package kr.savepick.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** BR-026, docs/00-status.md G-6 — ORD-YYYYMMDD-NNNNNN 형식. */
class OrderNumberGeneratorTest {

    @Test
    @DisplayName("ORD_YYYYMMDD_NNNNNN_형식으로_생성한다")
    void ORD_YYYYMMDD_NNNNNN_형식으로_생성한다() {
        String orderNo = OrderNumberGenerator.generate(LocalDate.of(2026, 8, 28), 123);
        assertThat(orderNo).isEqualTo("ORD-20260828-000123");
    }

    @Test
    @DisplayName("시퀀스_값이_6자리를_넘으면_그대로_붙인다")
    void 시퀀스_값이_6자리를_넘으면_그대로_붙인다() {
        String orderNo = OrderNumberGenerator.generate(LocalDate.of(2026, 8, 28), 1_234_567);
        assertThat(orderNo).isEqualTo("ORD-20260828-1234567");
    }
}
