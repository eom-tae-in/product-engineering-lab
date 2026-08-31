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
import kr.savepick.stock.domain.StockChangeReason;
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
 * API-110·111 (11-api-spec.md §8, FR-046, FR-047, docs/16-test-plan.md TC-082·TC-083·TC-084).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class StockQueryServiceIntegrationTest {

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private StockAdjustService stockAdjustService;

    @Autowired
    private InventoryHoldService inventoryHoldService;

    @Autowired
    private StockQueryService stockQueryService;

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
    @DisplayName("TC_082_재고_현황_4값이_항상_일관된다")
    void TC_082_재고_현황_4값이_항상_일관된다() {
        Product product = registerSample();
        Long adminId = registerAdmin();
        stockAdjustService.adjust(product.getId(), 10, null, adminId);
        Long customerId = registerCustomer();
        Long orderId = insertDummyOrder(customerId);
        inventoryHoldService.createHolds(orderId, Map.of(product.getId(), 3), ActorType.CUSTOMER, customerId);

        StockQueryService.OverviewPage page = stockQueryService.getOverview(false, 0, 20);

        var item = page.items().stream().filter(i -> i.productId().equals(product.getId())).findFirst().orElseThrow();
        assertThat(item.totalQuantity()).isEqualTo(item.availableQuantity() + item.heldQuantity() + item.confirmedQuantity());
        assertThat(item.consistent()).isTrue();
        assertThat(item.heldQuantity()).isEqualTo(3);
        assertThat(item.availableQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("onlyUnavailable이면_판매가능수량_0인_상품만_반환한다")
    void onlyUnavailable이면_판매가능수량_0인_상품만_반환한다() {
        Product product = registerSample();
        Long adminId = registerAdmin();
        stockAdjustService.adjust(product.getId(), 1, null, adminId);
        Long customerId = registerCustomer();
        Long orderId = insertDummyOrder(customerId);
        inventoryHoldService.createHolds(orderId, Map.of(product.getId(), 1), ActorType.CUSTOMER, customerId);

        StockQueryService.OverviewPage page = stockQueryService.getOverview(true, 0, 20);

        assertThat(page.items()).anySatisfy(item -> {
            assertThat(item.productId()).isEqualTo(product.getId());
            assertThat(item.availableQuantity()).isZero();
        });
    }

    @Test
    @DisplayName("TC_083_재고_변경_이력이_사유별로_구분돼_조회된다")
    void TC_083_재고_변경_이력이_사유별로_구분돼_조회된다() {
        Product product = registerSample();
        Long adminId = registerAdmin();
        stockAdjustService.adjust(product.getId(), 10, null, adminId);
        Long customerId = registerCustomer();
        Long orderId = insertDummyOrder(customerId);
        inventoryHoldService.createHolds(orderId, Map.of(product.getId(), 2), ActorType.CUSTOMER, customerId);

        StockQueryService.LedgerPage page = stockQueryService.getLedger(product.getId(), 0, 20);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items()).extracting(StockQueryService.LedgerItem::reason)
                .containsExactlyInAnyOrder(StockChangeReason.ADMIN_ADJUST, StockChangeReason.HOLD);
    }

    @Test
    @DisplayName("존재하지_않는_상품의_이력을_조회하면_NOT_FOUND다")
    void 존재하지_않는_상품의_이력을_조회하면_NOT_FOUND다() {
        assertThatThrownBy(() -> stockQueryService.getLedger(999_999L, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
