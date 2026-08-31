package kr.savepick.account.application;

import static org.assertj.core.api.Assertions.assertThat;

import kr.savepick.account.domain.OrderPermission;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-007 (BR-023). 제한 이력이 있는 경우는 order 도메인이 생긴 뒤 통합 테스트로 보강한다
 * (OrderNoShowReadDao 주석 참고 — orders/member_restrictions 생성은 이 슬라이스 범위 밖).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class OrderRestrictionServiceIntegrationTest {

    @Autowired
    private SignUpService signUpService;

    @Autowired
    private OrderRestrictionService orderRestrictionService;

    @Test
    @DisplayName("제한_이력이_없으면_ALLOWED이고_노쇼_목록이_비어있다")
    void 제한_이력이_없으면_ALLOWED이고_노쇼_목록이_비어있다() {
        var signUp = signUpService.signUp("noshow1@test.com", "password123", "사용자", "01011112222");

        var status = orderRestrictionService.getStatus(signUp.member().getId());

        assertThat(status.orderPermission()).isEqualTo(OrderPermission.ALLOWED);
        assertThat(status.recentNoShowCount()).isZero();
        assertThat(status.noShowOrders()).isEmpty();
        assertThat(status.restrictedUntil()).isNull();
        assertThat(status.windowDays()).isEqualTo(30);
    }
}
