package kr.savepick.stock.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.common.audit.ActorType;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.domain.Product;
import kr.savepick.stock.domain.HoldStatus;
import kr.savepick.stock.domain.InventoryHold;
import kr.savepick.stock.domain.StockChangeReason;
import kr.savepick.stock.infrastructure.InventoryHoldJpaRepository;
import kr.savepick.stock.infrastructure.StockLedgerJpaRepository;
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
 * 13번 §2.2 (b) 쓰기 지연 정리 — product_stocks 행을 잠그는 트랜잭션(선점 생성·재고 조정)이
 * 만료된 HELD 선점을 실제로 회수하는지 검증한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class ExpiredHoldReclaimerIntegrationTest {

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private StockAdjustService stockAdjustService;

    @Autowired
    private InventoryHoldService inventoryHoldService;

    @Autowired
    private InventoryHoldJpaRepository inventoryHoldJpaRepository;

    @Autowired
    private StockLedgerJpaRepository stockLedgerJpaRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ServerClock serverClock;

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
    @DisplayName("만료된_선점은_다음_쓰기_트랜잭션에서_회수되고_재고가_복구된다")
    void 만료된_선점은_다음_쓰기_트랜잭션에서_회수되고_재고가_복구된다() {
        LocalDateTime now = serverClock.now();
        Product product = productRegisterService.register(
                "만료회수 상품", "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, 1L);
        Long adminId = registerMember("admin");
        stockAdjustService.adjust(product.getId(), 5, null, adminId);

        Long expiredCustomer = registerMember("expired");
        Long expiredOrderId = insertDummyOrder(expiredCustomer);
        inventoryHoldService.createHolds(expiredOrderId, Map.of(product.getId(), 3), ActorType.CUSTOMER, expiredCustomer);

        // 방금 만든 선점의 expires_at을 과거로 되돌려 "이미 만료됐지만 아직 회수되지 않은" 상태를 흉내낸다.
        jdbcTemplate.update(
                "UPDATE inventory_holds SET expires_at = ? WHERE order_id = ? AND product_id = ?",
                java.sql.Timestamp.valueOf(now.minusMinutes(1)), expiredOrderId, product.getId());

        // 새 선점 시도 — product_stocks 행을 잠그는 시점에 만료 선점이 지연 회수돼야 한다 (13번 §2.2).
        Long newCustomer = registerMember("new");
        Long newOrderId = insertDummyOrder(newCustomer);
        List<InventoryHold> newHolds =
                inventoryHoldService.createHolds(newOrderId, Map.of(product.getId(), 4), ActorType.CUSTOMER, newCustomer);

        assertThat(newHolds).hasSize(1);

        InventoryHold expiredHold = inventoryHoldJpaRepository.findByOrderId(expiredOrderId).get(0);
        assertThat(expiredHold.getStatus()).isEqualTo(HoldStatus.EXPIRED);

        boolean hasExpireLedger = stockLedgerJpaRepository.findByProductIdOrderByOccurredAtDesc(
                        product.getId(), org.springframework.data.domain.PageRequest.of(0, 20))
                .getContent().stream()
                .anyMatch(ledger -> ledger.getReason() == StockChangeReason.HOLD_EXPIRE && expiredOrderId.equals(ledger.getOrderId()));
        assertThat(hasExpireLedger).isTrue();
    }
}
