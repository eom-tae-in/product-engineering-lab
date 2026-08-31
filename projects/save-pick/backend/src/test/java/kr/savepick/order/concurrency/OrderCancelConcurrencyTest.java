package kr.savepick.order.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.cart.application.CartOwner;
import kr.savepick.cart.application.CartService;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.time.ServerClock;
import kr.savepick.order.application.OrderCancelService;
import kr.savepick.order.application.OrderDraftService;
import kr.savepick.order.application.PickupSlotAssignService;
import kr.savepick.order.domain.OrderStatus;
import kr.savepick.order.domain.OrderRepository;
import kr.savepick.order.payment.PaymentAttemptService;
import kr.savepick.pickup.application.PickupSlotProvisionService;
import kr.savepick.pickup.application.PickupSlotQueryService;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.application.ProductStatusService;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.stock.application.StockAdjustService;
import kr.savepick.stock.infrastructure.ProductStockJpaRepository;
import kr.savepick.support.ProductTestFixtures;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * docs/16-test-plan.md TC-065 [동시성] 고객·관리자 동시 취소 (13번 §5·§10).
 * 클래스 레벨 {@code @Transactional}을 붙이지 않는다 — 서로 다른 물리 트랜잭션이 필요하다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class OrderCancelConcurrencyTest {

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
    private OrderRepository orderJpaRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ServerClock serverClock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        // 클래스 레벨 @Transactional이 없어(동시성 테스트라 물리 트랜잭션을 분리해야 한다)
        // provisionForDate로 만든 오늘·내일 표준 시간대 그리드가 그대로 남는다 — 다른 테스트
        // 클래스(예: PickupSlotReserveServiceIntegrationTest)의 고정 시각과 충돌하지 않도록
        // 이 테스트가 만든 주문이 참조하지 않는 슬롯은 정리한다.
        jdbcTemplate.update(
                "DELETE FROM pickup_slots WHERE slot_date IN (CURRENT_DATE, CURRENT_DATE + 1) "
                        + "AND id NOT IN (SELECT pickup_slot_id FROM orders WHERE pickup_slot_id IS NOT NULL)");
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

    private Long confirmOrder(Long customerId, Product product) {
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), 2);
        var draft = orderDraftService.createDraft(customerId, null);
        LocalDate today = serverClock.now().toLocalDate();
        pickupSlotProvisionService.provisionForDate(today, serverClock.now());
        pickupSlotProvisionService.provisionForDate(today.plusDays(1), serverClock.now());
        LocalDateTime farEnough = serverClock.now().plusMinutes(90);
        PickupSlotQueryService.SelectableSlotsResult slots =
                pickupSlotAssignService.getSelectableSlots(draft.order().getId(), customerId, null);
        var selectable = slots.slots().stream()
                .filter(PickupSlotQueryService.SlotView::selectable)
                .filter(s -> s.startAt().isAfter(farEnough))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("충분히 여유 있는 선택 가능한 시간대가 없다 — 테스트 전제가 깨졌다"));
        pickupSlotAssignService.assign(draft.order().getId(), customerId, selectable.slotId());
        var result = paymentAttemptService.pay(draft.order().getId(), customerId, draft.order().getTotalAmount(), UUID.randomUUID().toString());
        return result.orderId();
    }

    @Test
    @DisplayName("TC_065_고객과_관리자가_같은_주문을_동시에_취소하면_한쪽만_성공하고_재고는_한번만_변한다")
    void TC_065_고객과_관리자가_같은_주문을_동시에_취소하면_한쪽만_성공하고_재고는_한번만_변한다() throws Exception {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("동시취소상품", 10, adminId);
        Long orderId = confirmOrder(customerId, product);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        executor = Executors.newFixedThreadPool(2);

        Callable<String> customerCancel = () -> attemptCustomerCancel(orderId, customerId, ready, start);
        Callable<String> adminCancel = () -> attemptAdminCancel(orderId, adminId, ready, start);

        Future<String> f1 = executor.submit(customerCancel);
        Future<String> f2 = executor.submit(adminCancel);

        assertThat(ready.await(10, TimeUnit.SECONDS)).as("두 스레드가 준비를 마쳐야 한다").isTrue();
        start.countDown();

        String r1 = f1.get(15, TimeUnit.SECONDS);
        String r2 = f2.get(15, TimeUnit.SECONDS);

        assertThat(List.of(r1, r2)).containsExactlyInAnyOrder("SUCCESS", "CANCEL_NOT_ALLOWED");

        var order = orderJpaRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);

        var stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
        // 재고는 한 번만 변한다 — 확정 판매 2개가 정확히 한 번만 판매 가능으로 복구된다.
        assertThat(stock.getAvailableQuantity()).isEqualTo(10);
        assertThat(stock.getConfirmedQuantity()).isZero();
    }

    private String attemptCustomerCancel(Long orderId, Long customerId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        awaitStart(start);
        try {
            orderCancelService.cancelByCustomer(orderId, customerId);
            return "SUCCESS";
        } catch (BusinessException e) {
            return e.errorCode().name();
        }
    }

    private String attemptAdminCancel(Long orderId, Long adminId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        awaitStart(start);
        try {
            orderCancelService.cancelByAdmin(orderId, adminId, "관리자 사유");
            return "SUCCESS";
        } catch (BusinessException e) {
            return e.errorCode().name();
        }
    }

    private void awaitStart(CountDownLatch start) {
        try {
            start.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
