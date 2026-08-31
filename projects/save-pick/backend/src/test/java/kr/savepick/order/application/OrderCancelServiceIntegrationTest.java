package kr.savepick.order.application;

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
import kr.savepick.stock.domain.StockChangeReason;
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
 * API-025·API-117 (11-api-spec.md §5·9, docs/16-test-plan.md TC-059~065·095~097, 13번 §5).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class OrderCancelServiceIntegrationTest {

    @Autowired
    private OrderDraftService orderDraftService;

    @Autowired
    private PickupSlotAssignService pickupSlotAssignService;

    @Autowired
    private PickupSlotProvisionService pickupSlotProvisionService;

    @Autowired
    private PaymentAttemptService paymentAttemptService;

    @Autowired
    private OrderCancelService orderCancelService;

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

    /** 결제까지 마쳐 CONFIRMED 상태인 주문을 만든다. */
    private Long confirmOrder(Long customerId, Product product, int quantity) {
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), quantity);
        var draft = orderDraftService.createDraft(customerId, null);

        LocalDate today = serverClock.now().toLocalDate();
        pickupSlotProvisionService.provisionForDate(today, serverClock.now());
        pickupSlotProvisionService.provisionForDate(today.plusDays(1), serverClock.now());

        // 취소 마감(BR-018, 픽업 시작 1시간 전)보다 예약 마감(BR-015, 시작 30분 전)이 더 늦게까지
        // 열려 있어, "선택 가능한" 슬롯 중에도 이미 취소 마감을 지난 슬롯(예: 40분 뒤 시작)이
        // 섞여 있을 수 있다. "정상적으로 취소 가능한" 시나리오를 검증하려면 충분히 먼 슬롯을
        // 골라야 한다 — 명시적으로 마감을 되돌리는 테스트(setCancelableUntil)와는 별개다.
        PickupSlotQueryService.SelectableSlotsResult slots =
                pickupSlotAssignService.getSelectableSlots(draft.order().getId(), customerId, null);
        LocalDateTime farEnough = serverClock.now().plusMinutes(90);
        var selectable = slots.slots().stream()
                .filter(PickupSlotQueryService.SlotView::selectable)
                .filter(slot -> slot.startAt().isAfter(farEnough))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("충분히 여유 있는 선택 가능한 시간대가 없다 — 테스트 전제가 깨졌다"));
        pickupSlotAssignService.assign(draft.order().getId(), customerId, selectable.slotId());

        var result = paymentAttemptService.pay(draft.order().getId(), customerId, draft.order().getTotalAmount(), UUID.randomUUID().toString());
        entityManager.clear();
        return result.orderId();
    }

    private void setCancelableUntil(Long orderId, LocalDateTime cancelableUntil) {
        jdbcTemplate.update("UPDATE orders SET cancelable_until = ? WHERE id = ?", Timestamp.valueOf(cancelableUntil), orderId);
        entityManager.clear();
    }

    @Test
    @DisplayName("TC_059_060_마감_전_고객_취소는_확정판매를_판매가능으로_복구하고_정원을_반납한다")
    void TC_059_060_마감_전_고객_취소는_확정판매를_판매가능으로_복구하고_정원을_반납한다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("취소복구상품", 10, adminId);
        Long orderId = confirmOrder(customerId, product, 3);
        var orderBefore = orderJpaRepository.findById(orderId).orElseThrow();
        int reservedBefore = pickupSlotJpaRepository.findById(orderBefore.getPickupSlotId()).orElseThrow().getReservedCount();

        OrderCancelService.CancelResult result = orderCancelService.cancelByCustomer(orderId, customerId);

        assertThat(result.slotReleased()).isTrue();
        assertThat(result.stockResults()).hasSize(1);
        assertThat(result.stockResults().get(0).restored()).isTrue();
        assertThat(result.stockResults().get(0).reason()).isEqualTo(StockChangeReason.CANCEL_RESTORE);

        var order = orderJpaRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getStockSettledAt()).isNotNull();

        var stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(stock.getConfirmedQuantity()).isZero();
        assertThat(stock.getAvailableQuantity()).isEqualTo(10);

        var slot = pickupSlotJpaRepository.findById(orderBefore.getPickupSlotId()).orElseThrow();
        assertThat((int) slot.getReservedCount()).isEqualTo(reservedBefore - 1);
    }

    @Test
    @DisplayName("TC_061_마감_후_취소는_재고를_복구하지_않고_총재고를_깎아_폐기_수량으로_남긴다")
    void TC_061_마감_후_취소는_재고를_복구하지_않고_총재고를_깎아_폐기_수량으로_남긴다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("마감후취소상품", 10, adminId);
        Long orderId = confirmOrder(customerId, product, 2);

        // 상품 마감 시각을 과거로 당겨 "이미 마감 시각이 지난" 상태를 흉내낸다(관리자 취소는
        // 고객 취소 마감 시각과 무관하므로 여기서는 관리자 취소로 검증한다, BR-020).
        jdbcTemplate.update("UPDATE products SET closing_at = ? WHERE id = ?",
                Timestamp.valueOf(serverClock.now().minusMinutes(1)), product.getId());
        entityManager.clear();

        OrderCancelService.CancelResult result = orderCancelService.cancelByAdmin(orderId, adminId, "상품 이상 발견");

        assertThat(result.stockResults().get(0).restored()).isFalse();
        assertThat(result.stockResults().get(0).reason()).isEqualTo(StockChangeReason.CANCEL_DISCARD);

        var stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(stock.getConfirmedQuantity()).isZero();
        assertThat(stock.getDiscardedQuantity()).isEqualTo(2);
        assertThat(stock.getTotalQuantity()).isEqualTo(8);
        assertThat(stock.getAvailableQuantity()).isEqualTo(8);
    }

    @Test
    @DisplayName("TC_062_픽업_1시간_이내_고객_취소는_CANCEL_DEADLINE_PASSED다")
    void TC_062_픽업_1시간_이내_고객_취소는_CANCEL_DEADLINE_PASSED다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("마감임박취소상품", 10, adminId);
        Long orderId = confirmOrder(customerId, product, 1);
        setCancelableUntil(orderId, serverClock.now().minusMinutes(1));

        assertThatThrownBy(() -> orderCancelService.cancelByCustomer(orderId, customerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CANCEL_DEADLINE_PASSED);
    }

    @Test
    @DisplayName("TC_063_097_종료_상태_주문은_고객_관리자_모두_CANCEL_NOT_ALLOWED다")
    void TC_063_097_종료_상태_주문은_고객_관리자_모두_CANCEL_NOT_ALLOWED다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("종료상태취소상품", 10, adminId);
        Long orderId = confirmOrder(customerId, product, 1);
        orderCancelService.cancelByCustomer(orderId, customerId);

        assertThatThrownBy(() -> orderCancelService.cancelByCustomer(orderId, customerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CANCEL_NOT_ALLOWED);
        assertThatThrownBy(() -> orderCancelService.cancelByAdmin(orderId, adminId, "사유"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CANCEL_NOT_ALLOWED);
    }

    @Test
    @DisplayName("TC_064_취소_재전송은_CANCEL_NOT_ALLOWED이고_재고는_한번만_변한다")
    void TC_064_취소_재전송은_CANCEL_NOT_ALLOWED이고_재고는_한번만_변한다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("취소재전송상품", 10, adminId);
        Long orderId = confirmOrder(customerId, product, 1);

        orderCancelService.cancelByCustomer(orderId, customerId);
        assertThatThrownBy(() -> orderCancelService.cancelByCustomer(orderId, customerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CANCEL_NOT_ALLOWED);

        var stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(stock.getAvailableQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("TC_095_관리자_취소는_사유가_없으면_CANCEL_REASON_REQUIRED다")
    void TC_095_관리자_취소는_사유가_없으면_CANCEL_REASON_REQUIRED다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("사유필수상품", 10, adminId);
        Long orderId = confirmOrder(customerId, product, 1);

        assertThatThrownBy(() -> orderCancelService.cancelByAdmin(orderId, adminId, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CANCEL_REASON_REQUIRED);
        assertThatThrownBy(() -> orderCancelService.cancelByAdmin(orderId, adminId, "   "))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CANCEL_REASON_REQUIRED);
    }

    @Test
    @DisplayName("TC_096_관리자_취소는_고객_취소_마감_시각과_무관하게_실행된다")
    void TC_096_관리자_취소는_고객_취소_마감_시각과_무관하게_실행된다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("관리자마감무관상품", 10, adminId);
        Long orderId = confirmOrder(customerId, product, 1);
        setCancelableUntil(orderId, serverClock.now().minusMinutes(1));

        OrderCancelService.CancelResult result = orderCancelService.cancelByAdmin(orderId, adminId, "관리자 사유");

        assertThat(result.canceledBy()).isEqualTo("ADMIN");
        assertThat(result.cancelReason()).isEqualTo("관리자 사유");
        var order = orderJpaRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo("관리자 사유");
    }
}
