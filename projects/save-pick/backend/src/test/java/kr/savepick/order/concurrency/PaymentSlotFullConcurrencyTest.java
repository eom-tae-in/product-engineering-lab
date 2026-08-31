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
import kr.savepick.order.application.OrderDraftService;
import kr.savepick.order.application.PickupSlotAssignService;
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
import kr.savepick.stock.domain.HoldStatus;
import kr.savepick.stock.infrastructure.InventoryHoldJpaRepository;
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
 * docs/16-test-plan.md TC-054 [동시성] 슬롯 마지막 1자리 동시 결제 (13번 §8, §10).
 * 클래스 레벨 {@code @Transactional}을 붙이지 않는다 — 두 스레드가 서로 다른 물리 트랜잭션을
 * 가져야 실제 행 락 경합을 재현한다. 타임아웃을 걸어 정지 위험을 막는다(과제 지시).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class PaymentSlotFullConcurrencyTest {

    @Autowired
    private OrderDraftService orderDraftService;

    @Autowired
    private PickupSlotAssignService pickupSlotAssignService;

    @Autowired
    private PickupSlotProvisionService pickupSlotProvisionService;

    @Autowired
    private PaymentAttemptService paymentAttemptService;

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
    private PickupSlotRepository pickupSlotJpaRepository;

    @Autowired
    private OrderRepository orderJpaRepository;

    @Autowired
    private InventoryHoldJpaRepository inventoryHoldJpaRepository;

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
        // hoursAhead를 크게 잡아 ProductTestFixtures가 항상 "내일 20:00"로 떨어지게 한다 —
        // 아래에서 내일 슬롯을 선점 대상으로 쓰므로, 마감 시각이 그 슬롯보다 먼저 오면
        // SLOT_AFTER_PRODUCT_CLOSING으로 테스트 전제가 깨진다.
        Product product = productRegisterService.register(
                name, "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 20), (short) 5, adminId);
        stockAdjustService.adjust(product.getId(), stock, null, adminId);
        productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);
        return product;
    }

    /** 주문서 생성 후 지정한 슬롯으로 지정까지 마친다. */
    private record PreparedOrder(Long orderId, int totalAmount) {
    }

    private PreparedOrder prepareOrderOnSlot(Long customerId, Product product, Long slotId) {
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), 1);
        var draft = orderDraftService.createDraft(customerId, null);
        pickupSlotAssignService.assign(draft.order().getId(), customerId, slotId);
        return new PreparedOrder(draft.order().getId(), draft.order().getTotalAmount());
    }

    @Test
    @DisplayName("TC_054_정원_마지막_1자리를_두_고객이_동시에_결제하면_한_건만_CONFIRMED이고_다른_한_건은_SLOT_FULL이며_선점은_유지된다")
    void TC_054_정원_마지막_1자리를_두_고객이_동시에_결제하면_한_건만_CONFIRMED이고_다른_한_건은_SLOT_FULL이며_선점은_유지된다() throws Exception {
        LocalDateTime now = serverClock.now();
        Long adminId = registerCustomer("admin");
        Product product = registerOnSaleProduct("정원마지막자리상품", 10, adminId);

        LocalDate today = now.toLocalDate();
        pickupSlotProvisionService.provisionForDate(today, now);
        pickupSlotProvisionService.provisionForDate(today.plusDays(1), now);

        // 내일 첫 시간대(10:00)를 고른다 — 상품 마감(내일 20:00)보다 한참 이르고, 지금으로부터도
        // 항상 충분히 멀어 예약 마감(SLOT_CLOSED)에 걸리지 않는다.
        var slots = pickupSlotJpaRepository.findByStoreIdAndSlotDateOrderByStartAtAsc((short) 1, today.plusDays(1));
        var targetSlot = slots.stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("대상 슬롯을 찾지 못했다 — 테스트 전제가 깨졌다"));
        jdbcTemplate.update("UPDATE pickup_slots SET capacity = 1 WHERE id = ?", targetSlot.getId());

        Long memberA = registerCustomer("a");
        Long memberB = registerCustomer("b");
        PreparedOrder orderA = prepareOrderOnSlot(memberA, product, targetSlot.getId());
        PreparedOrder orderB = prepareOrderOnSlot(memberB, product, targetSlot.getId());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        executor = Executors.newFixedThreadPool(2);

        Callable<String> taskA = () -> attemptPay(orderA.orderId(), memberA, orderA.totalAmount(), ready, start);
        Callable<String> taskB = () -> attemptPay(orderB.orderId(), memberB, orderB.totalAmount(), ready, start);

        Future<String> futureA = executor.submit(taskA);
        Future<String> futureB = executor.submit(taskB);

        assertThat(ready.await(10, TimeUnit.SECONDS)).as("두 스레드가 준비를 마쳐야 한다").isTrue();
        start.countDown();

        String resultA = futureA.get(15, TimeUnit.SECONDS);
        String resultB = futureB.get(15, TimeUnit.SECONDS);

        assertThat(List.of(resultA, resultB)).containsExactlyInAnyOrder("SUCCEEDED", "SLOT_FULL");

        var slotAfter = pickupSlotJpaRepository.findById(targetSlot.getId()).orElseThrow();
        assertThat(slotAfter.getReservedCount()).isEqualTo((short) 1);

        Long loserOrderId = resultA.equals("SLOT_FULL") ? orderA.orderId() : orderB.orderId();
        var loserOrder = orderJpaRepository.findById(loserOrderId).orElseThrow();
        // 선점은 유지된다 — 결제 실패로 취급되지 않고 주문은 여전히 PENDING이다(트랜잭션 전체 롤백).
        assertThat(loserOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        var loserHold = inventoryHoldJpaRepository.findByOrderId(loserOrderId).get(0);
        assertThat(loserHold.getStatus()).isEqualTo(HoldStatus.HELD);
    }

    private String attemptPay(Long orderId, Long memberId, int amount, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                return "START_TIMEOUT";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "INTERRUPTED";
        }
        try {
            var result = paymentAttemptService.pay(orderId, memberId, amount, UUID.randomUUID().toString());
            return result.succeeded() ? "SUCCEEDED" : "FAILED";
        } catch (BusinessException e) {
            return e.errorCode().name();
        }
    }
}
