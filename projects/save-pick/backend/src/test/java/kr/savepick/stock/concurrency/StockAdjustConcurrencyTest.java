package kr.savepick.stock.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
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
 * 13번 §6, docs/16-test-plan.md TC-085 [동시성] 관리자 재고 축소와 주문서 생성 동시 진행.
 * 같은 {@code product_stocks} 행 락을 공유하므로 순서와 무관하게
 * {@code total_quantity >= held_quantity + confirmed_quantity}가 항상 유지돼야 한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class StockAdjustConcurrencyTest {

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

    private Long registerMember(String tag) {
        Member member = Member.registerCustomer(
                tag + "-" + java.util.UUID.randomUUID() + "@test.com", "hash", "회원", "01011112222", serverClock.now());
        return memberRepository.save(member).getId();
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
    @DisplayName("TC_085_108_관리자_축소와_고객_선점이_동시에_진행돼도_총재고는_선점확정_이상을_유지한다")
    void TC_085_108_관리자_축소와_고객_선점이_동시에_진행돼도_총재고는_선점확정_이상을_유지한다() throws Exception {
        LocalDateTime now = serverClock.now();
        Product product = productRegisterService.register(
                "동시_축소_상품", "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, 1L);
        Long adminId = registerMember("admin");
        stockAdjustService.adjust(product.getId(), 20, null, adminId);

        Long customerId = registerMember("customer");
        Long orderId = insertDummyOrder(customerId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        executor = Executors.newFixedThreadPool(2);

        // 축소(20 -> 8)와 5개 선점이 어느 순서로 실행되든 8 >= 5(held) + 0(confirmed)이 성립해
        // 두 트랜잭션 모두 성공해야 한다 (13번 §6 "두 순서의 결과" 표).
        Callable<String> adjustTask = () -> attemptAdjust(product.getId(), adminId, ready, start);
        Callable<String> holdTask = () -> attemptHold(orderId, product.getId(), customerId, ready, start);

        Future<String> adjustFuture = executor.submit(adjustTask);
        Future<String> holdFuture = executor.submit(holdTask);

        assertThat(ready.await(10, TimeUnit.SECONDS)).as("두 스레드가 준비를 마쳐야 한다").isTrue();
        start.countDown();

        String adjustResult = adjustFuture.get(15, TimeUnit.SECONDS);
        String holdResult = holdFuture.get(15, TimeUnit.SECONDS);

        assertThat(adjustResult).isEqualTo("SUCCESS");
        assertThat(holdResult).isEqualTo("SUCCESS");

        ProductStock stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(stock.getTotalQuantity()).isGreaterThanOrEqualTo(stock.getHeldQuantity() + stock.getConfirmedQuantity());
        assertThat(stock.getTotalQuantity()).isEqualTo(8);
        assertThat(stock.getHeldQuantity()).isEqualTo(5);
    }

    private String attemptAdjust(Long productId, Long adminId, CountDownLatch ready, CountDownLatch start) {
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
            stockAdjustService.adjust(productId, 8, "동시성 테스트 축소", adminId);
            return "SUCCESS";
        } catch (BusinessException e) {
            return e.errorCode().name();
        }
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
            inventoryHoldService.createHolds(orderId, Map.of(productId, 5), ActorType.CUSTOMER, memberId);
            return "SUCCESS";
        } catch (BusinessException e) {
            return e.errorCode().name();
        }
    }
}
