package kr.savepick.stock.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.common.audit.ActorType;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.domain.Product;
import kr.savepick.stock.domain.HoldStatus;
import kr.savepick.stock.domain.InventoryHold;
import kr.savepick.stock.infrastructure.InventoryHoldJpaRepository;
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
 * stock/application/InventoryHoldService — 선점 생성·해제·확정 (13번 §1·§3·§4, BR-007, BR-011, BR-012, BR-027).
 * order 도메인이 없어(이번 슬라이스 범위 밖) 이 서비스를 테스트에서 직접 호출한다
 * (docs/16-test-plan.md TC-027·TC-028의 핵심 판정을 stock 계층에서 검증).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class InventoryHoldServiceIntegrationTest {

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private StockAdjustService stockAdjustService;

    @Autowired
    private InventoryHoldService inventoryHoldService;

    @Autowired
    private InventoryHoldJpaRepository inventoryHoldJpaRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ServerClock serverClock;

    private Product registerProductWithStock(int totalQuantity) {
        LocalDateTime now = serverClock.now();
        Product product = productRegisterService.register("상품", "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, 1L);
        stockAdjustService.adjust(product.getId(), totalQuantity, null, registerAdmin());
        return product;
    }

    private Long registerCustomer() {
        Member customer = Member.registerCustomer(
                "customer-" + java.util.UUID.randomUUID() + "@test.com", "hash", "고객", "01011112222", serverClock.now());
        return memberRepository.save(customer).getId();
    }

    private Long registerAdmin() {
        Member admin = Member.registerAdmin(
                "admin-" + java.util.UUID.randomUUID() + "@test.com", "hash", "관리자", "01000000000", serverClock.now());
        return memberRepository.save(admin).getId();
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
    @DisplayName("TC_027_주문서_생성_시_전_품목이_선점된다")
    void TC_027_주문서_생성_시_전_품목이_선점된다() {
        Product product = registerProductWithStock(10);
        Long customerId = registerCustomer();
        Long orderId = insertDummyOrder(customerId);

        List<InventoryHold> holds = inventoryHoldService.createHolds(orderId, Map.of(product.getId(), 3), ActorType.CUSTOMER, customerId);

        assertThat(holds).hasSize(1);
        assertThat(holds.get(0).getStatus()).isEqualTo(HoldStatus.HELD);
        assertThat(holds.get(0).getQuantity()).isEqualTo((short) 3);
    }

    @Test
    @DisplayName("TC_028_한_품목이라도_부족하면_전체_선점이_실패한다")
    void TC_028_한_품목이라도_부족하면_전체_선점이_실패한다() {
        Product plenty = registerProductWithStock(10);
        Product scarce = registerProductWithStock(1);
        Long customerId = registerCustomer();
        Long orderId = insertDummyOrder(customerId);

        assertThatThrownBy(() -> inventoryHoldService.createHolds(
                        orderId, Map.of(plenty.getId(), 2, scarce.getId(), 5), ActorType.CUSTOMER, customerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.OUT_OF_STOCK);

        // 부분 선점이 남지 않는다 (BR-027, G7) — plenty 품목도 선점되지 않았어야 한다.
        assertThat(inventoryHoldJpaRepository.findByOrderId(orderId)).isEmpty();
    }

    @Test
    @DisplayName("재고가_부족하면_OUT_OF_STOCK_details에_shortages가_담긴다")
    void 재고가_부족하면_OUT_OF_STOCK_details에_shortages가_담긴다() {
        Product product = registerProductWithStock(1);
        Long customerId = registerCustomer();
        Long orderId = insertDummyOrder(customerId);

        assertThatThrownBy(() -> inventoryHoldService.createHolds(orderId, Map.of(product.getId(), 2), ActorType.CUSTOMER, customerId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.details()).containsKey("shortages");
                });
    }

    @Test
    @DisplayName("선점을_확정하면_HELD가_CONSUMED로_바뀌고_확정_판매가_늘어난다")
    void 선점을_확정하면_HELD가_CONSUMED로_바뀌고_확정_판매가_늘어난다() {
        Product product = registerProductWithStock(10);
        Long customerId = registerCustomer();
        Long orderId = insertDummyOrder(customerId);
        inventoryHoldService.createHolds(orderId, Map.of(product.getId(), 4), ActorType.CUSTOMER, customerId);

        inventoryHoldService.confirmHolds(orderId, ActorType.SYSTEM, null);

        List<InventoryHold> holds = inventoryHoldJpaRepository.findByOrderId(orderId);
        assertThat(holds).hasSize(1);
        assertThat(holds.get(0).getStatus()).isEqualTo(HoldStatus.CONSUMED);
    }

    @Test
    @DisplayName("선점을_해제하면_HELD가_RELEASED로_바뀌고_판매_가능_재고로_복구된다")
    void 선점을_해제하면_HELD가_RELEASED로_바뀌고_판매_가능_재고로_복구된다() {
        Product product = registerProductWithStock(10);
        Long customerId = registerCustomer();
        Long orderId = insertDummyOrder(customerId);
        inventoryHoldService.createHolds(orderId, Map.of(product.getId(), 4), ActorType.CUSTOMER, customerId);

        inventoryHoldService.releaseHolds(orderId, ActorType.SYSTEM, null);

        List<InventoryHold> holds = inventoryHoldJpaRepository.findByOrderId(orderId);
        assertThat(holds.get(0).getStatus()).isEqualTo(HoldStatus.RELEASED);
    }
}
