package kr.savepick.order.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.cart.application.CartOwner;
import kr.savepick.cart.application.CartService;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.order.application.OrderDraftService;
import kr.savepick.order.application.PickupSlotAssignService;
import kr.savepick.order.domain.OrderStatus;
import kr.savepick.order.domain.OrderRepository;
import kr.savepick.order.infrastructure.PaymentAttemptJpaRepository;
import kr.savepick.pickup.application.PickupSlotProvisionService;
import kr.savepick.pickup.application.PickupSlotQueryService;
import kr.savepick.pickup.domain.PickupSlotRepository;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.application.ProductStatusService;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.stock.application.StockAdjustService;
import kr.savepick.stock.domain.HoldStatus;
import kr.savepick.stock.infrastructure.InventoryHoldJpaRepository;
import kr.savepick.stock.infrastructure.ProductStockJpaRepository;
import kr.savepick.support.ProductTestFixtures;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-022 (11-api-spec.md §5, docs/16-test-plan.md TC-045~052, 13번 §4).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class PaymentAttemptServiceIntegrationTest {

    @Autowired
    private OrderDraftService orderDraftService;

    @Autowired
    private PickupSlotAssignService pickupSlotAssignService;

    @Autowired
    private PickupSlotProvisionService pickupSlotProvisionService;

    @Autowired
    private PaymentAttemptService paymentAttemptService;

    @Autowired
    private DefaultVirtualPaymentGateway virtualPaymentGateway;

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private ProductStatusService productStatusService;

    @Autowired
    private StockAdjustService stockAdjustService;

    @Autowired
    private ProductStockJpaRepository productStockJpaRepository;

    @Autowired
    private InventoryHoldJpaRepository inventoryHoldJpaRepository;

    @Autowired
    private PickupSlotRepository pickupSlotJpaRepository;

    @Autowired
    private OrderRepository orderJpaRepository;

    @Autowired
    private PaymentAttemptJpaRepository paymentAttemptJpaRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ServerClock serverClock;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @AfterEach
    void resetGateway() {
        virtualPaymentGateway.reset();
    }

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

    /** 주문서 생성 → 슬롯 지정까지 마친 PENDING 주문을 준비한다. */
    private PreparedOrder prepareOrder(Long customerId, Product product, int quantity) {
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), quantity);
        OrderDraftService.DraftResult draft = orderDraftService.createDraft(customerId, null);

        LocalDate today = serverClock.now().toLocalDate();
        pickupSlotProvisionService.provisionForDate(today, serverClock.now());
        pickupSlotProvisionService.provisionForDate(today.plusDays(1), serverClock.now());

        PickupSlotQueryService.SelectableSlotsResult slots =
                pickupSlotAssignService.getSelectableSlots(draft.order().getId(), customerId, null);
        var selectable = slots.slots().stream()
                .filter(PickupSlotQueryService.SlotView::selectable)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("선택 가능한 시간대가 없다 — 테스트 전제가 깨졌다"));
        pickupSlotAssignService.assign(draft.order().getId(), customerId, selectable.slotId());

        return new PreparedOrder(draft.order().getId(), customerId, draft.order().getTotalAmount(), selectable.slotId());
    }

    private void setHoldExpiresAt(Long orderId, LocalDateTime holdExpiresAt) {
        jdbcTemplate.update("UPDATE orders SET hold_expires_at = ? WHERE id = ?", Timestamp.valueOf(holdExpiresAt), orderId);
        entityManager.clear();
    }

    @Test
    @DisplayName("TC_045_가상_결제_성공시_주문이_확정되고_재고가_확정판매로_전환되고_정원이_점유되고_픽업번호가_발급된다")
    void TC_045_가상_결제_성공시_주문이_확정되고_재고가_확정판매로_전환되고_정원이_점유되고_픽업번호가_발급된다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("결제성공상품", 10, adminId);
        PreparedOrder prepared = prepareOrder(customerId, product, 2);
        int reservedBefore = pickupSlotJpaRepository.findById(prepared.slotId()).orElseThrow().getReservedCount();

        PaymentResult result = paymentAttemptService.pay(prepared.orderId(), customerId, prepared.totalAmount(), UUID.randomUUID().toString());

        assertThat(result.succeeded()).isTrue();
        assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.pickupNumber()).isNotNull();
        assertThat(result.pickupNumber().intValue()).isGreaterThanOrEqualTo(1);

        var stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(stock.getHeldQuantity()).isZero();
        assertThat(stock.getConfirmedQuantity()).isEqualTo(2);

        var slot = pickupSlotJpaRepository.findById(prepared.slotId()).orElseThrow();
        assertThat((int) slot.getReservedCount()).isEqualTo(reservedBefore + 1);

        var hold = inventoryHoldJpaRepository.findByOrderId(prepared.orderId()).get(0);
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.CONSUMED);

        var order = orderJpaRepository.findById(prepared.orderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getCancelableUntil()).isNotNull();
        assertThat(order.getNoShowDueAt()).isNotNull();
    }

    @Test
    @DisplayName("TC_048_결제_1_2회_실패해도_선점이_유지되고_재고가_변하지_않는다")
    void TC_048_결제_1_2회_실패해도_선점이_유지되고_재고가_변하지_않는다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("결제실패상품", 10, adminId);
        PreparedOrder prepared = prepareOrder(customerId, product, 1);

        virtualPaymentGateway.forceNextResult(VirtualPaymentGateway.PaymentJudgement.failure(PaymentFailureReason.DECLINED));
        PaymentResult first = paymentAttemptService.pay(prepared.orderId(), customerId, prepared.totalAmount(), UUID.randomUUID().toString());
        assertThat(first.succeeded()).isFalse();
        assertThat(first.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(first.paymentAttemptRemaining()).isEqualTo(2);

        virtualPaymentGateway.forceNextResult(VirtualPaymentGateway.PaymentJudgement.failure(PaymentFailureReason.TIMEOUT));
        PaymentResult second = paymentAttemptService.pay(prepared.orderId(), customerId, prepared.totalAmount(), UUID.randomUUID().toString());
        assertThat(second.succeeded()).isFalse();
        assertThat(second.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(second.paymentAttemptRemaining()).isEqualTo(1);
        assertThat(second.failureReason()).isEqualTo(PaymentFailureReason.TIMEOUT);

        var hold = inventoryHoldJpaRepository.findByOrderId(prepared.orderId()).get(0);
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.HELD);
        var stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(stock.getHeldQuantity()).isEqualTo(1);
        assertThat(stock.getConfirmedQuantity()).isZero();
    }

    @Test
    @DisplayName("TC_049_결제_3회_실패하면_주문이_FAILED되고_선점이_즉시_해제되어_재고가_복구된다")
    void TC_049_결제_3회_실패하면_주문이_FAILED되고_선점이_즉시_해제되어_재고가_복구된다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("삼회실패상품", 10, adminId);
        PreparedOrder prepared = prepareOrder(customerId, product, 3);

        for (int i = 0; i < 2; i++) {
            virtualPaymentGateway.forceNextResult(VirtualPaymentGateway.PaymentJudgement.failure(PaymentFailureReason.DECLINED));
            paymentAttemptService.pay(prepared.orderId(), customerId, prepared.totalAmount(), UUID.randomUUID().toString());
        }
        virtualPaymentGateway.forceNextResult(VirtualPaymentGateway.PaymentJudgement.failure(PaymentFailureReason.DECLINED));
        PaymentResult third = paymentAttemptService.pay(prepared.orderId(), customerId, prepared.totalAmount(), UUID.randomUUID().toString());

        assertThat(third.succeeded()).isFalse();
        assertThat(third.status()).isEqualTo(OrderStatus.FAILED);
        assertThat(third.holdReleased()).isTrue();
        assertThat(third.paymentAttemptRemaining()).isZero();

        var hold = inventoryHoldJpaRepository.findByOrderId(prepared.orderId()).get(0);
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.RELEASED);
        var stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(stock.getHeldQuantity()).isZero();
        assertThat(stock.getAvailableQuantity()).isEqualTo(10);
        assertThat(stock.getConfirmedQuantity()).isZero();

        assertThatThrownBy(() -> paymentAttemptService.pay(prepared.orderId(), customerId, prepared.totalAmount(), UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("TC_050_선점_만료_후_결제를_시도하면_HOLD_EXPIRED이고_주문이_EXPIRED로_종결된다")
    void TC_050_선점_만료_후_결제를_시도하면_HOLD_EXPIRED이고_주문이_EXPIRED로_종결된다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("만료결제상품", 10, adminId);
        PreparedOrder prepared = prepareOrder(customerId, product, 1);
        setHoldExpiresAt(prepared.orderId(), serverClock.now().minusSeconds(1));

        assertThatThrownBy(() -> paymentAttemptService.pay(prepared.orderId(), customerId, prepared.totalAmount(), UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.HOLD_EXPIRED);

        var order = orderJpaRepository.findById(prepared.orderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        var hold = inventoryHoldJpaRepository.findByOrderId(prepared.orderId()).get(0);
        assertThat(hold.getStatus()).isEqualTo(HoldStatus.EXPIRED);
        assertThat(paymentAttemptJpaRepository.findByOrderIdOrderByAttemptNo(prepared.orderId())).isEmpty();
    }

    @Test
    @DisplayName("TC_035_결제_요청_금액이_주문서_확정_금액과_다르면_AMOUNT_MISMATCH이고_시도기록을_만들지_않는다")
    void TC_035_결제_요청_금액이_주문서_확정_금액과_다르면_AMOUNT_MISMATCH이고_시도기록을_만들지_않는다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("금액불일치상품", 10, adminId);
        PreparedOrder prepared = prepareOrder(customerId, product, 1);

        assertThatThrownBy(() -> paymentAttemptService.pay(prepared.orderId(), customerId, prepared.totalAmount() + 1, UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.AMOUNT_MISMATCH);

        assertThat(paymentAttemptJpaRepository.findByOrderIdOrderByAttemptNo(prepared.orderId())).isEmpty();
        var order = orderJpaRepository.findById(prepared.orderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("TC_051_107_같은_Idempotency_Key로_결제를_두번_보내도_시도_횟수는_1만_증가하고_같은_결과를_반환한다")
    void TC_051_107_같은_Idempotency_Key로_결제를_두번_보내도_시도_횟수는_1만_증가하고_같은_결과를_반환한다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("멱등결제상품", 10, adminId);
        PreparedOrder prepared = prepareOrder(customerId, product, 1);
        String key = UUID.randomUUID().toString();

        PaymentResult first = paymentAttemptService.pay(prepared.orderId(), customerId, prepared.totalAmount(), key);
        PaymentResult second = paymentAttemptService.pay(prepared.orderId(), customerId, prepared.totalAmount(), key);

        assertThat(first.succeeded()).isTrue();
        assertThat(second.succeeded()).isTrue();
        assertThat(second.pickupNumber()).isEqualTo(first.pickupNumber());
        assertThat(paymentAttemptJpaRepository.findByOrderIdOrderByAttemptNo(prepared.orderId())).hasSize(1);
        var order = orderJpaRepository.findById(prepared.orderId()).orElseThrow();
        assertThat(order.getPaymentAttemptCount()).isEqualTo((short) 1);
    }

    private record PreparedOrder(Long orderId, Long memberId, int totalAmount, Long slotId) {
    }
}
