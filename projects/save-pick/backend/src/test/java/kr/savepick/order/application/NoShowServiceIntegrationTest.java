package kr.savepick.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import kr.savepick.account.application.OrderRestrictionService;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.account.domain.OrderPermission;
import kr.savepick.cart.application.CartOwner;
import kr.savepick.cart.application.CartService;
import kr.savepick.common.time.ServerClock;
import kr.savepick.order.domain.OrderStatus;
import kr.savepick.order.domain.OrderRepository;
import kr.savepick.order.payment.PaymentAttemptService;
import kr.savepick.pickup.application.PickupSlotProvisionService;
import kr.savepick.pickup.application.PickupSlotQueryService;
import kr.savepick.pickup.domain.PickupSlotRepository;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.application.ProductStatusService;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.stock.application.StockAdjustService;
import kr.savepick.stock.infrastructure.ProductStockJpaRepository;
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
 * BATCH-03 (11-api-spec.md §11, docs/16-test-plan.md TC-067·068, BR-021~023, 05-state-rules.md §8).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class NoShowServiceIntegrationTest {

    @Autowired
    private OrderDraftService orderDraftService;

    @Autowired
    private PickupSlotAssignService pickupSlotAssignService;

    @Autowired
    private PickupSlotProvisionService pickupSlotProvisionService;

    @Autowired
    private PaymentAttemptService paymentAttemptService;

    @Autowired
    private NoShowService noShowService;

    @Autowired
    private OrderRestrictionService orderRestrictionService;

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private ProductStatusService productStatusService;

    @Autowired
    private StockAdjustService stockAdjustService;

    @Autowired
    private ProductStockJpaRepository productStockJpaRepository;

    @Autowired
    private PickupSlotRepository pickupSlotJpaRepository;

    @Autowired
    private OrderRepository orderJpaRepository;

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

    private Long confirmOrder(Long customerId, Product product) {
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), 1);
        var draft = orderDraftService.createDraft(customerId, null);
        LocalDate today = serverClock.now().toLocalDate();
        pickupSlotProvisionService.provisionForDate(today, serverClock.now());
        pickupSlotProvisionService.provisionForDate(today.plusDays(1), serverClock.now());
        PickupSlotQueryService.SelectableSlotsResult slots =
                pickupSlotAssignService.getSelectableSlots(draft.order().getId(), customerId, null);
        var selectable = slots.slots().stream().filter(PickupSlotQueryService.SlotView::selectable).findFirst().orElseThrow();
        pickupSlotAssignService.assign(draft.order().getId(), customerId, selectable.slotId());
        var result = paymentAttemptService.pay(draft.order().getId(), customerId, draft.order().getTotalAmount(), UUID.randomUUID().toString());
        entityManager.clear();
        return result.orderId();
    }

    private void expireNoShowDueAt(Long orderId) {
        jdbcTemplate.update(
                "UPDATE orders SET no_show_due_at = ? WHERE id = ?",
                Timestamp.valueOf(serverClock.now().minusMinutes(1)), orderId);
        entityManager.clear();
    }

    @Test
    @DisplayName("TC_067_유예_경과_주문은_노쇼로_전환되고_재고는_불변하며_정원은_반납된다")
    void TC_067_유예_경과_주문은_노쇼로_전환되고_재고는_불변하며_정원은_반납된다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("노쇼상품", 10, adminId);
        Long orderId = confirmOrder(customerId, product);
        var orderBefore = orderJpaRepository.findById(orderId).orElseThrow();
        int reservedBefore = pickupSlotJpaRepository.findById(orderBefore.getPickupSlotId()).orElseThrow().getReservedCount();
        var stockBefore = productStockJpaRepository.findByProductId(product.getId()).orElseThrow().getConfirmedQuantity();
        expireNoShowDueAt(orderId);

        boolean converted = noShowService.convertToNoShow(orderId, serverClock.now());

        assertThat(converted).isTrue();
        var order = orderJpaRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NO_SHOW);
        assertThat(order.getNoShowAt()).isNotNull();

        var stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(stock.getConfirmedQuantity()).isEqualTo(stockBefore);

        var slot = pickupSlotJpaRepository.findById(orderBefore.getPickupSlotId()).orElseThrow();
        assertThat((int) slot.getReservedCount()).isEqualTo(reservedBefore - 1);
    }

    @Test
    @DisplayName("같은_주문을_다시_노쇼_전환하면_아무일도_일어나지_않는다")
    void 같은_주문을_다시_노쇼_전환하면_아무일도_일어나지_않는다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("노쇼중복상품", 10, adminId);
        Long orderId = confirmOrder(customerId, product);
        expireNoShowDueAt(orderId);

        assertThat(noShowService.convertToNoShow(orderId, serverClock.now())).isTrue();
        assertThat(noShowService.convertToNoShow(orderId, serverClock.now())).isFalse();
    }

    @Test
    @DisplayName("TC_068_최근_30일_노쇼가_정확히_3회에_도달하면_7일_제한이_생기고_그_이후에는_새로_만들지_않는다")
    void TC_068_최근_30일_노쇼가_정확히_3회에_도달하면_7일_제한이_생기고_그_이후에는_새로_만들지_않는다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");

        Long firstOrderId = null;
        for (int i = 0; i < 3; i++) {
            Product product = registerOnSaleProduct("반복노쇼상품" + i, 10, adminId);
            Long orderId = confirmOrder(customerId, product);
            if (i == 0) {
                firstOrderId = orderId;
            }
            expireNoShowDueAt(orderId);
            noShowService.convertToNoShow(orderId, serverClock.now());
        }

        var statusAfterThird = orderRestrictionService.getStatus(customerId);
        assertThat(statusAfterThird.recentNoShowCount()).isEqualTo(3);
        assertThat(statusAfterThird.orderPermission()).isEqualTo(OrderPermission.RESTRICTED);
        assertThat(statusAfterThird.restrictedUntil()).isNotNull();
        assertThat(statusAfterThird.restrictedUntil()).isAfter(serverClock.now().plusDays(6));

        var member = memberRepository.findById(customerId).orElseThrow();
        assertThat(member.getOrderPermission()).isEqualTo(OrderPermission.RESTRICTED);

        long restrictionCountAfterThird = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_restrictions WHERE member_id = ?", Long.class, customerId);
        assertThat(restrictionCountAfterThird).isEqualTo(1);

        // 4번째 노쇼 상당 재호출 — 이미 활성 제한이 있으므로(활성 제한이 없을 때만 생성한다,
        // 11번 BATCH-03) 새 제재를 만들지 않는다. 노쇼 제한 상태에서는 신규 주문서 생성 자체가
        // 막히므로(05 §7.4) 실제로 4번째 주문을 새로 만드는 대신 판정 로직만 재호출해 검증한다.
        boolean createdAgain = orderRestrictionService.applyNoShowRestriction(customerId, firstOrderId, serverClock.now());
        assertThat(createdAgain).isFalse();
        long restrictionCountAfterRetry = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM member_restrictions WHERE member_id = ?", Long.class, customerId);
        assertThat(restrictionCountAfterRetry).isEqualTo(1);
    }
}
