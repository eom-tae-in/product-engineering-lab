package kr.savepick.stock.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Map;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.common.audit.ActorType;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.domain.Product;
import kr.savepick.support.ProductTestFixtures;
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
 * API-109 (11-api-spec.md §8, BR-006, BR-025, docs/16-test-plan.md TC-079·TC-080·TC-081).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class StockAdjustServiceIntegrationTest {

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private StockAdjustService stockAdjustService;

    @Autowired
    private InventoryHoldService inventoryHoldService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ServerClock serverClock;

    private Product registerSample() {
        LocalDateTime now = serverClock.now();
        return productRegisterService.register("상품", "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, 1L);
    }

    private Long registerCustomer() {
        Member customer = Member.registerCustomer(
                "customer-" + java.util.UUID.randomUUID() + "@test.com", "hash", "고객", "01011112222", serverClock.now());
        return memberRepository.save(customer).getId();
    }

    private Long registerAdmin() {
        Member admin = Member.registerAdmin(
                "admin-" + java.util.UUID.randomUUID() + "@test.com", "hash", "관리자", "01000000000", serverClock.now());
        return memberRepository.save(admin).getId();
    }

    private Long insertDummyOrder(Long memberId) {
        LocalDateTime now = serverClock.now();
        String orderNo = "ORD-" + String.format("%015d", System.nanoTime() % 1_000_000_000_000_000L);
        return jdbcTemplate.queryForObject(
                "INSERT INTO orders (order_no, member_id, status, total_amount, contact_name, contact_phone, hold_expires_at) "
                        + "VALUES (?, ?, 'PENDING', 0, '테스트', '01000000000', ?) RETURNING id",
                Long.class, orderNo, memberId, java.sql.Timestamp.valueOf(now.plusMinutes(10)));
    }

    @Test
    @DisplayName("TC_079_재고를_처음_등록하면_총재고와_판매가능수량이_늘어난다")
    void TC_079_재고를_처음_등록하면_총재고와_판매가능수량이_늘어난다() {
        Product product = registerSample();
        Long adminId = registerAdmin();

        StockAdjustService.AdjustResult result = stockAdjustService.adjust(product.getId(), 17, "실물 진열 수량 확인", adminId);

        assertThat(result.before().total()).isZero();
        assertThat(result.after().total()).isEqualTo(17);
        assertThat(result.after().available()).isEqualTo(17);
    }

    @Test
    @DisplayName("TC_080_확정6_선점2일_때_7로_축소하면_STOCK_BELOW_COMMITTED다")
    void TC_080_확정6_선점2일_때_7로_축소하면_STOCK_BELOW_COMMITTED다() {
        Product product = registerSample();
        Long adminId = registerAdmin();
        stockAdjustService.adjust(product.getId(), 20, null, adminId);

        // 확정 판매 6을 만든다 (선점 후 확정).
        Long customer1 = registerCustomer();
        Long orderId = insertDummyOrder(customer1);
        inventoryHoldService.createHolds(orderId, Map.of(product.getId(), 6), ActorType.CUSTOMER, customer1);
        inventoryHoldService.confirmHolds(orderId, ActorType.SYSTEM, null);
        // 선점 중 2를 만든다 (HELD 상태 유지, 확정하지 않는다).
        Long customer2 = registerCustomer();
        Long orderId2 = insertDummyOrder(customer2);
        inventoryHoldService.createHolds(orderId2, Map.of(product.getId(), 2), ActorType.CUSTOMER, customer2);

        assertThatThrownBy(() -> stockAdjustService.adjust(product.getId(), 7, null, adminId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.errorCode()).isEqualTo(ErrorCode.STOCK_BELOW_COMMITTED);
                    assertThat(be.details().get("minimumSettableQuantity")).isEqualTo(8);
                });
    }

    @Test
    @DisplayName("TC_081_0_이상_재고만_허용된다")
    void TC_081_영_이상_재고만_허용된다() {
        Product product = registerSample();
        Long adminId = registerAdmin();
        StockAdjustService.AdjustResult result = stockAdjustService.adjust(product.getId(), 0, null, adminId);
        assertThat(result.after().total()).isZero();
    }

    @Test
    @DisplayName("존재하지_않는_상품의_재고를_조정하면_NOT_FOUND다")
    void 존재하지_않는_상품의_재고를_조정하면_NOT_FOUND다() {
        Long adminId = registerAdmin();
        assertThatThrownBy(() -> stockAdjustService.adjust(999_999L, 10, null, adminId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
