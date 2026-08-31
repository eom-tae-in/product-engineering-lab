package kr.savepick.stock.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.common.audit.ActorType;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.domain.Product;
import kr.savepick.stock.application.InventoryHoldService;
import kr.savepick.stock.application.StockAdjustService;
import kr.savepick.stock.domain.ProductStock;
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
 * 13번 §3, docs/16-test-plan.md TC-036 [동시성] 마지막 1개 동시 주문.
 * {@code product_stocks} 행 FOR UPDATE + CHK_stock_non_negative_available이 실제로
 * 초과 판매를 막는지 Testcontainers 실제 PostgreSQL로 검증한다.
 *
 * <p>테스트 메서드에 클래스 레벨 {@code @Transactional}을 붙이지 않는다 — 두 스레드가 서로 다른
 * 물리 트랜잭션(커넥션)을 가져야 실제 행 락 경합을 재현할 수 있다. 각 서비스 호출은 자체
 * {@code @Transactional} 경계를 갖고, 스레드가 끝나면 커밋되거나 롤백된다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class InventoryHoldConcurrencyTest {

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private StockAdjustService stockAdjustService;

    @Autowired
    private InventoryHoldService inventoryHoldService;

    @Autowired
    private ProductStockJpaRepository productStockJpaRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                tag + "-" + java.util.UUID.randomUUID() + "@test.com", "hash", "고객", "01011112222", serverClock.now());
        return memberRepository.save(customer).getId();
    }

    private Long insertDummyOrder(Long memberId) {
        LocalDateTime now = serverClock.now();
        String orderNo = "ORD-" + String.format("%015d", System.nanoTime() % 1_000_000_000_000_000L);
        return jdbcTemplate.queryForObject(
                "INSERT INTO orders (order_no, member_id, status, total_amount, contact_name, contact_phone, hold_expires_at) "
                        + "VALUES (?, ?, 'PENDING', 0, '테스트', '01000000000', ?) RETURNING id",
                Long.class, orderNo, memberId, java.sql.Timestamp.valueOf(now.plusMinutes(10)));
    }

    @Test
    @DisplayName("TC_036_마지막_1개_동시_선점_한_건만_성공한다")
    void TC_036_마지막_1개_동시_선점_한_건만_성공한다() throws Exception {
        LocalDateTime now = serverClock.now();
        Product product = productRegisterService.register(
                "마지막 1개 상품", "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, 1L);
        Long adminId = registerCustomer("admin"); // FK만 만족하면 되므로 role은 무관
        stockAdjustService.adjust(product.getId(), 1, null, adminId);

        Long memberA = registerCustomer("a");
        Long memberB = registerCustomer("b");
        Long orderA = insertDummyOrder(memberA);
        Long orderB = insertDummyOrder(memberB);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        executor = Executors.newFixedThreadPool(2);

        Callable<String> taskA = () -> attemptHold(orderA, product.getId(), memberA, ready, start);
        Callable<String> taskB = () -> attemptHold(orderB, product.getId(), memberB, ready, start);

        Future<String> futureA = executor.submit(taskA);
        Future<String> futureB = executor.submit(taskB);

        assertThat(ready.await(10, TimeUnit.SECONDS)).as("두 스레드가 준비를 마쳐야 한다").isTrue();
        start.countDown();

        String resultA = futureA.get(15, TimeUnit.SECONDS);
        String resultB = futureB.get(15, TimeUnit.SECONDS);

        assertThat(List.of(resultA, resultB)).containsExactlyInAnyOrder("SUCCESS", "OUT_OF_STOCK");

        ProductStock stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(stock.getHeldQuantity()).isEqualTo(1);
        assertThat(stock.getAvailableQuantity()).isZero();
        assertThat(stock.getTotalQuantity()).isEqualTo(1);
    }

    private String attemptHold(Long orderId, Long productId, Long memberId, CountDownLatch ready, CountDownLatch start) {
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
            inventoryHoldService.createHolds(orderId, Map.of(productId, 1), ActorType.CUSTOMER, memberId);
            return "SUCCESS";
        } catch (BusinessException e) {
            return e.errorCode().name();
        }
    }
}
