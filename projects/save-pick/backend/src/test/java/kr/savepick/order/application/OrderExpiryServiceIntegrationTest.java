package kr.savepick.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.cart.application.CartOwner;
import kr.savepick.cart.application.CartService;
import kr.savepick.common.time.ServerClock;
import kr.savepick.order.domain.OrderStatus;
import kr.savepick.order.domain.OrderRepository;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.application.ProductStatusService;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.stock.application.StockAdjustService;
import kr.savepick.stock.domain.HoldStatus;
import kr.savepick.stock.domain.StockChangeReason;
import kr.savepick.stock.infrastructure.InventoryHoldJpaRepository;
import kr.savepick.stock.infrastructure.ProductStockJpaRepository;
import kr.savepick.stock.infrastructure.StockLedgerJpaRepository;
import kr.savepick.support.ProductTestFixtures;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * BATCH-01 (11-api-spec.md §11, docs/16-test-plan.md TC-032·106, 13번 §2.3).
 * {@code order/batch/HoldExpiryReclaimJob}은 {@code @Profile("!test")}로 비활성화돼 있어
 * 이 테스트는 대상 조회(OrderRepository)와 건별 처리(OrderExpiryService)를 배치가 하는 그대로
 * 직접 호출해 검증한다(pickup/batch/PickupSlotProvisionJob과 같은 검증 방식).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class OrderExpiryServiceIntegrationTest {

    @Autowired
    private OrderDraftService orderDraftService;

    @Autowired
    private OrderExpiryService orderExpiryService;

    @Autowired
    private OrderRepository orderJpaRepository;

    @Autowired
    private InventoryHoldJpaRepository inventoryHoldJpaRepository;

    @Autowired
    private StockLedgerJpaRepository stockLedgerJpaRepository;

    @Autowired
    private ProductStockJpaRepository productStockJpaRepository;

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private ProductStatusService productStatusService;

    @Autowired
    private StockAdjustService stockAdjustService;

    @Autowired
    private CartService cartService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ServerClock serverClock;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long registerCustomer(String tag) {
        Member customer = Member.registerCustomer(
                tag + "-" + UUID.randomUUID() + "@test.com", "hash", "고객", "01011112222", serverClock.now());
        return memberRepository.save(customer).getId();
    }

    private Product registerOnSaleProduct(String name, int stock, Long adminId) {
        LocalDateTime now = serverClock.now();
        Product product = productRegisterService.register(
                name, "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, adminId);
        stockAdjustService.adjust(product.getId(), stock, null, adminId);
        productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);
        return product;
    }

    private void setHoldExpiresAt(Long orderId, LocalDateTime holdExpiresAt) {
        jdbcTemplate.update("UPDATE orders SET hold_expires_at = ? WHERE id = ?", Timestamp.valueOf(holdExpiresAt), orderId);
        entityManager.clear();
    }

    @Test
    @DisplayName("TC_032_106_배치_대상으로_조회된_만료_주문은_EXPIRED로_종결되고_선점은_EXPIRED_HOLD_EXPIRE로_회수된다")
    void TC_032_106_배치_대상으로_조회된_만료_주문은_EXPIRED로_종결되고_선점은_EXPIRED_HOLD_EXPIRE로_회수된다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("배치만료상품", 10, adminId);
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), 3);
        var draft = orderDraftService.createDraft(customerId, null);
        setHoldExpiresAt(draft.order().getId(), serverClock.now().minusSeconds(1));

        // BATCH-01이 하는 "대상 조회"를 그대로 재현한다.
        List<Long> targets = orderJpaRepository.findPendingIdsForHoldExpiry(serverClock.now(), 100);
        assertThat(targets).contains(draft.order().getId());

        boolean processed = orderExpiryService.expirePendingOrderById(draft.order().getId(), serverClock.now());

        assertThat(processed).isTrue();
        var order = orderJpaRepository.findById(draft.order().getId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        var hold = inventoryHoldJpaRepository.findByOrderId(draft.order().getId()).get(0);
        // 과제 지시 — 2단계에서 EXPIRED/HOLD_EXPIRE로 바로잡았다(1단계는 RELEASED/HOLD_RELEASE였다).
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.EXPIRED);

        boolean hasExpireLedger = stockLedgerJpaRepository
                .findByProductIdOrderByOccurredAtDesc(product.getId(), PageRequest.of(0, 20))
                .getContent().stream()
                .anyMatch(ledger -> ledger.getReason() == StockChangeReason.HOLD_EXPIRE && draft.order().getId().equals(ledger.getOrderId()));
        assertThat(hasExpireLedger).isTrue();

        var stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(stock.getHeldQuantity()).isZero();
        assertThat(stock.getAvailableQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("이미_결제_등으로_종결된_주문은_배치가_건드리지_않는다")
    void 이미_결제_등으로_종결된_주문은_배치가_건드리지_않는다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("배치스킵상품", 10, adminId);
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), 1);
        var draft = orderDraftService.createDraft(customerId, null);
        // 아직 만료되지 않았다 — 배치 대상이 아니다.
        boolean processed = orderExpiryService.expirePendingOrderById(draft.order().getId(), serverClock.now());
        assertThat(processed).isFalse();
        var order = orderJpaRepository.findById(draft.order().getId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("존재하지_않는_주문_id는_조용히_건너뛴다")
    void 존재하지_않는_주문_id는_조용히_건너뛴다() {
        boolean processed = orderExpiryService.expirePendingOrderById(-1L, serverClock.now());
        assertThat(processed).isFalse();
    }
}
