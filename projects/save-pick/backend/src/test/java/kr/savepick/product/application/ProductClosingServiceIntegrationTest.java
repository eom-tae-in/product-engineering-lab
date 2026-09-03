package kr.savepick.product.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.common.audit.ActorType;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductRepository;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.stock.application.InventoryHoldService;
import kr.savepick.stock.application.StockAdjustService;
import kr.savepick.stock.application.StockQueryService;
import kr.savepick.stock.domain.HoldStatus;
import kr.savepick.stock.domain.InventoryHold;
import kr.savepick.stock.infrastructure.InventoryHoldJpaRepository;
import kr.savepick.store.domain.Store;
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
 * BATCH-02 상품 마감 상태 전환 (11-api-spec.md §11, BR-030, docs/16-test-plan.md TC-070·TC-124).
 * 배치({@code product/batch/ProductClosingJob})는 {@code @Profile("!test")}라 테스트에서 로드되지
 * 않는다 — 배치가 호출하는 {@link ProductClosingService}와 대상 조회 쿼리를 직접 검증한다
 * (PickupSlotProvisionServiceIntegrationTest와 같은 전략).
 *
 * <p>마감 시각이 과거인 상품은 등록 API(API-103)로는 만들 수 없으므로(BR-003) 도메인 팩터리로
 * 직접 만든다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class ProductClosingServiceIntegrationTest {

    @Autowired
    private ProductClosingService productClosingService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockAdjustService stockAdjustService;

    @Autowired
    private StockQueryService stockQueryService;

    @Autowired
    private InventoryHoldService inventoryHoldService;

    @Autowired
    private InventoryHoldJpaRepository inventoryHoldJpaRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ServerClock serverClock;

    private Product saveProduct(ProductStatus status, LocalDateTime closingAt, LocalDateTime createdAt) {
        Product product = Product.register(
                Store.SINGLETON_ID, "마감 대상 상품", "설명", "1개", 10000, closingAt, (short) 5, createdAt);
        if (status == ProductStatus.ON_SALE) {
            product.startSale(createdAt);
        } else if (status == ProductStatus.HIDDEN) {
            product.startSale(createdAt);
            product.hide(createdAt);
        }
        return productRepository.save(product);
    }

    private Long registerMember(String prefix) {
        Member member = "admin".equals(prefix)
                ? Member.registerAdmin("admin-" + java.util.UUID.randomUUID() + "@test.com", "hash", "관리자", "01000000000", serverClock.now())
                : Member.registerCustomer("customer-" + java.util.UUID.randomUUID() + "@test.com", "hash", "고객", "01011112222", serverClock.now());
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

    @Test
    @DisplayName("TC_070_마감_시각이_지난_상품을_CLOSED로_전환하고_closed_at을_기록한다")
    void TC_070_마감_시각이_지난_상품을_CLOSED로_전환하고_closed_at을_기록한다() {
        LocalDateTime now = serverClock.now();
        Product product = saveProduct(ProductStatus.ON_SALE, now.minusMinutes(1), now.minusHours(3));

        boolean closed = productClosingService.closeIfDue(product.getId(), now);

        assertThat(closed).isTrue();
        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ProductStatus.CLOSED);
        assertThat(reloaded.getClosedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("아직_마감_시각이_지나지_않은_상품은_그대로_둔다")
    void 아직_마감_시각이_지나지_않은_상품은_그대로_둔다() {
        LocalDateTime now = serverClock.now();
        Product product = saveProduct(ProductStatus.ON_SALE, now.plusHours(1), now.minusHours(3));

        boolean closed = productClosingService.closeIfDue(product.getId(), now);

        assertThat(closed).isFalse();
        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(reloaded.getClosedAt()).isNull();
    }

    @Test
    @DisplayName("이미_CLOSED인_상품은_다시_전환하지_않는다")
    void 이미_CLOSED인_상품은_다시_전환하지_않는다() {
        LocalDateTime firstRunAt = serverClock.now();
        Product product = saveProduct(ProductStatus.ON_SALE, firstRunAt.minusMinutes(1), firstRunAt.minusHours(3));
        assertThat(productClosingService.closeIfDue(product.getId(), firstRunAt)).isTrue();

        boolean closedAgain = productClosingService.closeIfDue(product.getId(), firstRunAt.plusMinutes(1));

        assertThat(closedAgain).isFalse();
        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getClosedAt()).isEqualTo(firstRunAt);
    }

    @Test
    @DisplayName("DRAFT_ON_SALE_HIDDEN만_대상으로_조회되고_CLOSED와_미도달_상품은_제외된다")
    void DRAFT_ON_SALE_HIDDEN만_대상으로_조회되고_CLOSED와_미도달_상품은_제외된다() {
        LocalDateTime now = serverClock.now();
        Product draft = saveProduct(ProductStatus.DRAFT, now.minusMinutes(1), now.minusHours(3));
        Product onSale = saveProduct(ProductStatus.ON_SALE, now.minusMinutes(2), now.minusHours(3));
        Product hidden = saveProduct(ProductStatus.HIDDEN, now.minusMinutes(3), now.minusHours(3));
        Product notDue = saveProduct(ProductStatus.ON_SALE, now.plusHours(1), now.minusHours(3));
        Product alreadyClosed = saveProduct(ProductStatus.ON_SALE, now.minusMinutes(4), now.minusHours(3));
        productClosingService.closeIfDue(alreadyClosed.getId(), now);

        List<Long> targets = productRepository.findIdsForClosing(now, 200);

        assertThat(targets).contains(draft.getId(), onSale.getId(), hidden.getId());
        assertThat(targets).doesNotContain(notDue.getId(), alreadyClosed.getId());
    }

    @Test
    @DisplayName("BR_030_마감_전환은_기존_HELD_선점과_재고_수량을_건드리지_않는다")
    void BR_030_마감_전환은_기존_HELD_선점과_재고_수량을_건드리지_않는다() {
        LocalDateTime now = serverClock.now();
        Product product = saveProduct(ProductStatus.ON_SALE, now.minusMinutes(1), now.minusHours(3));
        Long adminId = registerMember("admin");
        stockAdjustService.adjust(product.getId(), 10, "초기 등록", adminId);
        Long customerId = registerMember("customer");
        Long orderId = insertPendingOrder(customerId);
        inventoryHoldService.createHolds(orderId, Map.of(product.getId(), 3), ActorType.CUSTOMER, customerId);

        boolean closed = productClosingService.closeIfDue(product.getId(), now);

        assertThat(closed).isTrue();
        List<InventoryHold> holds = inventoryHoldJpaRepository.findByOrderId(orderId);
        assertThat(holds).singleElement().satisfies(hold -> {
            assertThat(hold.getStatus()).isEqualTo(HoldStatus.HELD);
            assertThat(hold.getQuantity()).isEqualTo((short) 3);
        });
        var quantities = stockQueryService.getCorrectedQuantity(product.getId()).orElseThrow();
        assertThat(quantities.total()).isEqualTo(10);
        assertThat(quantities.held()).isEqualTo(3);
        assertThat(quantities.confirmed()).isZero();
    }
}
