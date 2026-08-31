package kr.savepick.order.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

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
import kr.savepick.order.application.PickupSlotAssignService;
import kr.savepick.order.domain.OrderRepository;
import kr.savepick.order.infrastructure.PaymentAttemptJpaRepository;
import kr.savepick.order.payment.PaymentAttemptService;
import kr.savepick.pickup.application.PickupSlotProvisionService;
import kr.savepick.pickup.application.PickupSlotQueryService;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.application.ProductStatusService;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.stock.application.StockAdjustService;
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
 * docs/16-test-plan.md TC-051·TC-107 [동시성] 같은 Idempotency-Key로 결제 2회 동시 전송 (13번 §4·§10).
 * {@code orders} 행 락(13번 §7.1 락 순서 1번)이 두 요청을 사실상 직렬화하므로, 나중 요청은
 * idempotency-key 재사용 응답을 받거나(순차적으로 겹치지 않는 경우) 이미 확정된 상태를 보고
 * {@code INVALID_ORDER_STATUS}를 받는다(동시에 시작해 시도 기록 삽입 전에 락을 기다리는 경우) —
 * 두 경우 모두 핵심 불변조건은 같다: 시도 기록은 정확히 1건만 만들어지고 시도 횟수는 1만 증가한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class PaymentIdempotencyConcurrencyTest {

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
    private OrderRepository orderJpaRepository;

    @Autowired
    private PaymentAttemptJpaRepository paymentAttemptJpaRepository;

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
    @DisplayName("TC_051_107_같은_Idempotency_Key_동시_전송은_시도_기록을_정확히_1건만_만든다")
    void TC_051_107_같은_Idempotency_Key_동시_전송은_시도_기록을_정확히_1건만_만든다() throws Exception {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("멱등동시결제상품", 10, adminId);
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
        String key = UUID.randomUUID().toString();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        executor = Executors.newFixedThreadPool(2);

        Callable<String> task = () -> attemptPay(orderId, customerId, totalAmount, key, ready, start);
        Future<String> f1 = executor.submit(task);
        Future<String> f2 = executor.submit(task);

        assertThat(ready.await(10, TimeUnit.SECONDS)).as("두 스레드가 준비를 마쳐야 한다").isTrue();
        start.countDown();

        String r1 = f1.get(15, TimeUnit.SECONDS);
        String r2 = f2.get(15, TimeUnit.SECONDS);

        // 두 결과 모두 "안전한" 결과여야 한다 — 성공, 같은 응답 재구성, 또는 그 사이 상태가 바뀌어
        // 거부됨. 무엇이든 핵심 불변조건(아래)만 지키면 된다.
        assertThat(r1).isIn("SUCCEEDED", "INVALID_ORDER_STATUS", "ALREADY_PAID");
        assertThat(r2).isIn("SUCCEEDED", "INVALID_ORDER_STATUS", "ALREADY_PAID");

        assertThat(paymentAttemptJpaRepository.findByOrderIdOrderByAttemptNo(orderId)).hasSize(1);
        var order = orderJpaRepository.findById(orderId).orElseThrow();
        assertThat(order.getPaymentAttemptCount()).isEqualTo((short) 1);
    }

    private String attemptPay(Long orderId, Long memberId, int amount, String key, CountDownLatch ready, CountDownLatch start) {
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
            var result = paymentAttemptService.pay(orderId, memberId, amount, key);
            return result.succeeded() ? "SUCCEEDED" : "FAILED";
        } catch (BusinessException e) {
            return e.errorCode().name();
        } catch (Exception e) {
            return "ERROR:" + e.getClass().getSimpleName();
        }
    }
}
