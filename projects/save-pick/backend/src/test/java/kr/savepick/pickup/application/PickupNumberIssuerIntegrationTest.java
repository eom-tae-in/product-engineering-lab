package kr.savepick.pickup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * BR-026, 10-erd.md §3.4. order 도메인의 결제 확정 서비스(2단계)가 아직 이 클래스를 호출하지
 * 않아(과제 지시) 이 슬라이스에서는 직접 호출해 검증한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class PickupNumberIssuerIntegrationTest {

    @Autowired
    private PickupNumberIssuer pickupNumberIssuer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("같은_영업일에는_1부터_순서대로_발급된다")
    void 같은_영업일에는_1부터_순서대로_발급된다() {
        LocalDate date = LocalDate.of(2026, 9, 20);

        assertThat(pickupNumberIssuer.issue(date)).isEqualTo((short) 1);
        assertThat(pickupNumberIssuer.issue(date)).isEqualTo((short) 2);
        assertThat(pickupNumberIssuer.issue(date)).isEqualTo((short) 3);
    }

    @Test
    @DisplayName("다른_영업일은_독립적으로_1부터_시작한다")
    void 다른_영업일은_독립적으로_1부터_시작한다() {
        LocalDate dayOne = LocalDate.of(2026, 9, 21);
        LocalDate dayTwo = LocalDate.of(2026, 9, 22);

        pickupNumberIssuer.issue(dayOne);
        pickupNumberIssuer.issue(dayOne);

        assertThat(pickupNumberIssuer.issue(dayTwo)).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("최대값_999에_도달하면_소진되어_PICKUP_NUMBER_EXHAUSTED를_던진다")
    void 최대값_999에_도달하면_소진되어_PICKUP_NUMBER_EXHAUSTED를_던진다() {
        LocalDate date = LocalDate.of(2026, 9, 23);
        jdbcTemplate.update("INSERT INTO pickup_number_seqs (business_date, last_number) VALUES (?, 999)", date);

        assertThatThrownBy(() -> pickupNumberIssuer.issue(date))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.PICKUP_NUMBER_EXHAUSTED);
    }
}
