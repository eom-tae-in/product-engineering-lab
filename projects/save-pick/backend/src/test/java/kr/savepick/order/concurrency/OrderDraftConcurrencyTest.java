package kr.savepick.order.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.context.ActiveProfiles;

/**
 * docs/16-test-plan.md TC-036·TC-105 [동시성] 마지막 1개 동시 주문. {@code InventoryHoldService}가
 * 이미 이 보장을 하므로(stock/concurrency/InventoryHoldConcurrencyTest), 여기서는 API 레벨
 * ({@code OrderDraftService})에서 한 번 더 얇게 확인한다 — 두 스레드가 서로 다른 물리 트랜잭션을
 * 가져야 하므로 클래스 레벨 {@code @Transactional}을 붙이지 않는다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class OrderDraftConcurrencyTest {

    @Autowired
    private OrderDraftService orderDraftService;

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private ProductStatusService productStatusService;

    @Autowired
    private StockAdjustService stockAdjustService;

    @Autowired
    private ProductStockJpaRepository productStockJpaRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ServerClock serverClock;

    private ExecutorService executor;

    @AfterEach
    void shutdownExecutor() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private Long registerCustomer(String tag) {
        Member customer = Member.registerCustomer(
                tag + "-" + UUID.randomUUID() + "@test.com", "hash", "고객", "01011112222", serverClock.now());
        return memberRepository.save(customer).getId();
    }

    @Test
    @DisplayName("TC_036_105_마지막_1개_동시_주문서_생성은_한_건만_성공한다")
    void TC_036_105_마지막_1개_동시_주문서_생성은_한_건만_성공한다() throws Exception {
        LocalDateTime now = serverClock.now();
        Long adminId = registerCustomer("admin");
        Product product = productRegisterService.register(
                "마지막1개주문상품", "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, adminId);
        stockAdjustService.adjust(product.getId(), 1, null, adminId);
        productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);

        Long memberA = registerCustomer("a");
        Long memberB = registerCustomer("b");
        cartService.addItem(CartOwner.ofMember(memberA), product.getId(), 1);
        cartService.addItem(CartOwner.ofMember(memberB), product.getId(), 1);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        executor = Executors.newFixedThreadPool(2);

        Callable<String> taskA = () -> attemptDraft(memberA, ready, start);
        Callable<String> taskB = () -> attemptDraft(memberB, ready, start);

        Future<String> futureA = executor.submit(taskA);
        Future<String> futureB = executor.submit(taskB);

        assertThat(ready.await(10, TimeUnit.SECONDS)).as("두 스레드가 준비를 마쳐야 한다").isTrue();
        start.countDown();

        String resultA = futureA.get(15, TimeUnit.SECONDS);
        String resultB = futureB.get(15, TimeUnit.SECONDS);

        assertThat(List.of(resultA, resultB)).containsExactlyInAnyOrder("SUCCESS", "OUT_OF_STOCK");

        var stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(stock.getHeldQuantity()).isEqualTo(1);
        assertThat(stock.getAvailableQuantity()).isZero();
        assertThat(stock.getTotalQuantity()).isEqualTo(1);
    }

    private String attemptDraft(Long memberId, CountDownLatch ready, CountDownLatch start) {
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
            orderDraftService.createDraft(memberId, null);
            return "SUCCESS";
        } catch (BusinessException e) {
            return e.errorCode().name();
        }
    }
}
