package kr.savepick.stock.application;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.Map;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.common.audit.ActorType;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.domain.Product;
import kr.savepick.stock.application.StockConsistencyCheckService.ConsistencyCheckResult;
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
 * BATCH-04 재고 정합성 점검 (11-api-spec.md §11, 13-inventory-concurrency.md §9.1,
 * docs/16-test-plan.md TC-086). 배치({@code stock/batch/StockConsistencyCheckJob})는
 * {@code @Profile("!test")}라 테스트에서 로드되지 않으므로, 배치가 건별로 호출하는
 * {@link StockConsistencyCheckService}를 직접 호출해 검증한다.
 *
 * <p>불일치는 정상 경로로는 만들 수 없어(그게 이 설계의 목적이다) 원시 SQL로 집계 컬럼만
 * 틀어 놓고 점검한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class StockConsistencyCheckServiceIntegrationTest {

    @Autowired
    private StockConsistencyCheckService stockConsistencyCheckService;

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

    @PersistenceContext
    private EntityManager entityManager;

    private Product registerSample() {
        LocalDateTime now = serverClock.now();
        return productRegisterService.register(
                "상품", "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, 1L);
    }

    private Long registerMember(String prefix) {
        Member member = "admin".equals(prefix)
                ? Member.registerAdmin(prefix + "-" + java.util.UUID.randomUUID() + "@test.com", "hash", "관리자", "01000000000", serverClock.now())
                : Member.registerCustomer(prefix + "-" + java.util.UUID.randomUUID() + "@test.com", "hash", "고객", "01011112222", serverClock.now());
        return memberRepository.save(member).getId();
    }

    private Long insertPendingOrder(Long memberId) {
        LocalDateTime now = serverClock.now();
        String orderNo = "ORD-" + String.format("%015d", System.nanoTime() % 1_000_000_000_000_000L);
        return jdbcTemplate.queryForObject(
                "INSERT INTO orders (order_no, member_id, status, total_amount, contact_name, contact_phone, hold_expires_at) "
                        + "VALUES (?, ?, 'PENDING', 0, '테스트', '01000000000', ?) RETURNING id",
                Long.class, orderNo, memberId, java.sql.Timestamp.valueOf(now.plusMinutes(10)));
    }

    /** 결제 성공(S4)으로 확정된 주문의 모습 — orders.status와 order_items를 함께 만든다. */
    private void markConfirmedWithItem(Long orderId, Product product, int quantity) {
        entityManager.flush();
        jdbcTemplate.update(
                "UPDATE orders SET status = 'CONFIRMED', hold_expires_at = NULL, confirmed_at = now() WHERE id = ?", orderId);
        insertOrderItem(orderId, product, quantity);
        entityManager.clear();
    }

    private void insertOrderItem(Long orderId, Product product, int quantity) {
        jdbcTemplate.update(
                "INSERT INTO order_items (order_id, product_id, product_name, sale_unit, quantity, "
                        + "original_unit_price, discount_rate, unit_price, line_amount, product_closing_at) "
                        + "VALUES (?, ?, ?, '1개', ?, ?, 0, ?, ?, ?)",
                orderId, product.getId(), product.getName(), quantity,
                product.getOriginalPrice(), product.getOriginalPrice(), product.getOriginalPrice() * quantity,
                java.sql.Timestamp.valueOf(product.getClosingAt()));
    }

    private void tamper(String sql, Object... args) {
        entityManager.flush();
        jdbcTemplate.update(sql, args);
        entityManager.clear();
    }

    private int readHeldQuantity(Long productId) {
        return jdbcTemplate.queryForObject("SELECT held_quantity FROM product_stocks WHERE product_id = ?", Integer.class, productId);
    }

    private int readConfirmedQuantity(Long productId) {
        return jdbcTemplate.queryForObject("SELECT confirmed_quantity FROM product_stocks WHERE product_id = ?", Integer.class, productId);
    }

    @Test
    @DisplayName("집계와_원장이_맞으면_불일치가_없다")
    void 집계와_원장이_맞으면_불일치가_없다() {
        Product product = registerSample();
        stockAdjustService.adjust(product.getId(), 10, "초기 등록", registerMember("admin"));

        Long holdingCustomerId = registerMember("customer");
        Long holdingOrderId = insertPendingOrder(holdingCustomerId);
        inventoryHoldService.createHolds(holdingOrderId, Map.of(product.getId(), 3), ActorType.CUSTOMER, holdingCustomerId);

        Long buyingCustomerId = registerMember("customer");
        Long confirmedOrderId = insertPendingOrder(buyingCustomerId);
        inventoryHoldService.createHolds(confirmedOrderId, Map.of(product.getId(), 2), ActorType.CUSTOMER, buyingCustomerId);
        inventoryHoldService.confirmHolds(confirmedOrderId, ActorType.CUSTOMER, buyingCustomerId);
        markConfirmedWithItem(confirmedOrderId, product, 2);

        ConsistencyCheckResult result = stockConsistencyCheckService.check(product.getId(), serverClock.now()).orElseThrow();

        assertThat(result.consistent()).isTrue();
        assertThat(result.actualHeldQuantity()).isEqualTo(3);
        assertThat(result.expectedHeldQuantity()).isEqualTo(3);
        assertThat(result.actualConfirmedQuantity()).isEqualTo(2);
        assertThat(result.expectedConfirmedQuantity()).isEqualTo(2);
        assertThat(result.actualTotalQuantity()).isEqualTo(10);
        assertThat(result.expectedTotalQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("TC_086a_선점_집계가_어긋나면_감지하고_값을_보정하지_않는다")
    void TC_086a_선점_집계가_어긋나면_감지하고_값을_보정하지_않는다() {
        Product product = registerSample();
        stockAdjustService.adjust(product.getId(), 10, "초기 등록", registerMember("admin"));
        Long customerId = registerMember("customer");
        Long orderId = insertPendingOrder(customerId);
        inventoryHoldService.createHolds(orderId, Map.of(product.getId(), 3), ActorType.CUSTOMER, customerId);
        tamper("UPDATE product_stocks SET held_quantity = 8 WHERE product_id = ?", product.getId());

        ConsistencyCheckResult result = stockConsistencyCheckService.check(product.getId(), serverClock.now()).orElseThrow();

        assertThat(result.consistent()).isFalse();
        assertThat(result.heldMatches()).isFalse();
        assertThat(result.actualHeldQuantity()).isEqualTo(8);
        assertThat(result.expectedHeldQuantity()).isEqualTo(3);
        assertThat(result.confirmedMatches()).isTrue();
        assertThat(result.totalMatches()).isTrue();
        // 점검은 경보만 남긴다 — 값은 틀어 놓은 그대로여야 한다 (13번 §9.1).
        assertThat(readHeldQuantity(product.getId())).isEqualTo(8);
    }

    @Test
    @DisplayName("TC_086b_확정_판매_수량이_어긋나면_감지하고_값을_보정하지_않는다")
    void TC_086b_확정_판매_수량이_어긋나면_감지하고_값을_보정하지_않는다() {
        Product product = registerSample();
        stockAdjustService.adjust(product.getId(), 10, "초기 등록", registerMember("admin"));
        Long customerId = registerMember("customer");
        Long orderId = insertPendingOrder(customerId);
        // 확정 주문은 있는데 confirmed_quantity에 반영되지 않은 상태를 만든다.
        markConfirmedWithItem(orderId, product, 2);

        ConsistencyCheckResult result = stockConsistencyCheckService.check(product.getId(), serverClock.now()).orElseThrow();

        assertThat(result.consistent()).isFalse();
        assertThat(result.confirmedMatches()).isFalse();
        assertThat(result.actualConfirmedQuantity()).isZero();
        assertThat(result.expectedConfirmedQuantity()).isEqualTo(2);
        assertThat(readConfirmedQuantity(product.getId())).isZero();
    }

    @Test
    @DisplayName("총_재고가_원장_누적과_어긋나면_감지하고_값을_보정하지_않는다")
    void 총_재고가_원장_누적과_어긋나면_감지하고_값을_보정하지_않는다() {
        Product product = registerSample();
        stockAdjustService.adjust(product.getId(), 10, "초기 등록", registerMember("admin"));
        tamper("UPDATE product_stocks SET total_quantity = 12 WHERE product_id = ?", product.getId());

        ConsistencyCheckResult result = stockConsistencyCheckService.check(product.getId(), serverClock.now()).orElseThrow();

        assertThat(result.totalMatches()).isFalse();
        assertThat(result.actualTotalQuantity()).isEqualTo(12);
        assertThat(result.expectedTotalQuantity()).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT total_quantity FROM product_stocks WHERE product_id = ?", Integer.class, product.getId()))
                .isEqualTo(12);
    }

    @Test
    @DisplayName("재고_행이_없는_상품은_점검_대상이_아니다")
    void 재고_행이_없는_상품은_점검_대상이_아니다() {
        Product product = registerSample();

        assertThat(stockConsistencyCheckService.check(product.getId(), serverClock.now())).isEmpty();
    }
}
