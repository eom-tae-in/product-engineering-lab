package kr.savepick.order.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import kr.savepick.order.application.OrderDraftService;
import kr.savepick.order.application.OrderExpiryService;
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
import kr.savepick.stock.domain.HoldStatus;
import kr.savepick.stock.domain.StockChangeReason;
import kr.savepick.stock.infrastructure.InventoryHoldJpaRepository;
import kr.savepick.stock.infrastructure.ProductStockJpaRepository;
import kr.savepick.stock.infrastructure.StockLedgerJpaRepository;
import kr.savepick.support.ProductTestFixtures;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * docs/16-test-plan.md TC-094 [동시성] BATCH-01과 결제 성공이 동시에 진행 (13번 §10).
 * 배치 쪽은 {@code order/batch/HoldExpiryReclaimJob}이 실제로 하는 일(대상 조회 후 건별
 * {@code OrderExpiryService#expirePendingOrderById} 호출)을 그대로 재현한다 — 잡 자체는
 * {@code @Profile("!test")}라 이 테스트에서 직접 기동되지 않는다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class HoldExpiryPaymentRaceConcurrencyTest {

    @Autowired
    private OrderDraftService orderDraftService;

    @Autowired
    private PickupSlotAssignService pickupSlotAssignService;

    @Autowired
    private PickupSlotProvisionService pickupSlotProvisionService;

    @Autowired
    private PaymentAttemptService paymentAttemptService;

    @Autowired
    private OrderExpiryService orderExpiryService;

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
    private OrderRepository orderJpaRepository;

    @Autowired
    private InventoryHoldJpaRepository inventoryHoldJpaRepository;

    @Autowired
    private StockLedgerJpaRepository stockLedgerJpaRepository;

    @Autowired
    private ProductStockJpaRepository productStockJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ServerClock serverClock;

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        // 클래스 레벨 @Transactional이 없어 provisionForDate로 만든 표준 시간대 그리드가 남는다 —
        // 다른 테스트 클래스의 고정 시각 슬롯과 충돌하지 않도록 정리한다.
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

    @Test
    @DisplayName("TC_094_BATCH_01_회수와_결제_요청이_동시에_진행돼도_조건부_UPDATE로_한쪽만_반영되고_원장은_중복되지_않는다")
    void TC_094_BATCH_01_회수와_결제_요청이_동시에_진행돼도_조건부_UPDATE로_한쪽만_반영되고_원장은_중복되지_않는다() throws Exception {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("만료결제경합상품", 10, adminId);
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), 1);
        var draft = orderDraftService.createDraft(customerId, null);

        LocalDate today = serverClock.now().toLocalDate();
        pickupSlotProvisionService.provisionForDate(today, serverClock.now());
        pickupSlotProvisionService.provisionForDate(today.plusDays(1), serverClock.now());
        PickupSlotQueryService.SelectableSlotsResult slots =
                pickupSlotAssignService.getSelectableSlots(draft.order().getId(), customerId, null);
        var selectable = slots.slots().stream().filter(PickupSlotQueryService.SlotView::selectable).findFirst().orElseThrow();
        pickupSlotAssignService.assign(draft.order().getId(), customerId, selectable.slotId());

        Long orderId = draft.order().getId();
        int totalAmount = draft.order().getTotalAmount();
        // 선점을 이미 만료된 것으로 흉내낸다 — BATCH-01 대상이면서 동시에 결제도 시도되는 상황.
        jdbcTemplate.update(
                "UPDATE orders SET hold_expires_at = ? WHERE id = ?",
                Timestamp.valueOf(serverClock.now().minusSeconds(1)), orderId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        executor = Executors.newFixedThreadPool(2);

        Callable<String> batchTask = () -> attemptBatchExpire(orderId, ready, start);
        Callable<String> paymentTask = () -> attemptPay(orderId, customerId, totalAmount, ready, start);

        Future<String> batchFuture = executor.submit(batchTask);
        Future<String> paymentFuture = executor.submit(paymentTask);

        assertThat(ready.await(10, TimeUnit.SECONDS)).as("두 스레드가 준비를 마쳐야 한다").isTrue();
        start.countDown();

        String batchResult = batchFuture.get(15, TimeUnit.SECONDS);
        String paymentResult = paymentFuture.get(15, TimeUnit.SECONDS);

        // 결제가 이겼다면(가상 결제는 즉시 성공하므로) CONFIRMED로, 배치가 이겼다면 EXPIRED로 끝난다 —
        // 둘 다 아닌 상태(예: 둘 다 처리됨)는 없어야 한다.
        var order = orderJpaRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isIn(OrderStatus.EXPIRED, OrderStatus.CONFIRMED);

        var hold = inventoryHoldJpaRepository.findByOrderId(orderId).get(0);
        if (order.getStatus() == OrderStatus.EXPIRED) {
            assertThat(hold.getStatus()).isEqualTo(HoldStatus.EXPIRED);
            assertThat(paymentResult).isEqualTo("HOLD_EXPIRED_OR_INVALID");
        } else {
            assertThat(hold.getStatus()).isEqualTo(HoldStatus.CONSUMED);
            assertThat(batchResult).isEqualTo("SKIPPED");
        }

        // 원장에 HOLD_EXPIRE·CONFIRM 등 같은 사유가 중복 기록되지 않는다(UQ_stock_ledgers_order_product_reason).
        var ledgers = stockLedgerJpaRepository.findByProductIdOrderByOccurredAtDesc(product.getId(), PageRequest.of(0, 20)).getContent();
        long matchingLedgerCount = ledgers.stream()
                .filter(l -> orderId.equals(l.getOrderId()))
                .filter(l -> l.getReason() == StockChangeReason.HOLD_EXPIRE || l.getReason() == StockChangeReason.CONFIRM)
                .count();
        assertThat(matchingLedgerCount).isEqualTo(1);
    }

    private String attemptBatchExpire(Long orderId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        awaitStart(start);
        boolean processed = orderExpiryService.expirePendingOrderById(orderId, serverClock.now());
        return processed ? "EXPIRED" : "SKIPPED";
    }

    private String attemptPay(Long orderId, Long memberId, int amount, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        awaitStart(start);
        try {
            var result = paymentAttemptService.pay(orderId, memberId, amount, UUID.randomUUID().toString());
            return result.succeeded() ? "SUCCEEDED" : "FAILED";
        } catch (BusinessException e) {
            // 배치가 먼저 이겼다면 결제 스레드는 HOLD_EXPIRED(자기 사전 검사가 감지) 또는
            // INVALID_ORDER_STATUS(배치가 이미 종결한 뒤 실행 트랜잭션의 상태 확인에 걸림) 중
            // 하나를 받는다 — 어느 쪽이든 결제는 반영되지 않는다.
            if (e.errorCode() == kr.savepick.common.error.ErrorCode.HOLD_EXPIRED
                    || e.errorCode() == kr.savepick.common.error.ErrorCode.INVALID_ORDER_STATUS) {
                return "HOLD_EXPIRED_OR_INVALID";
            }
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
