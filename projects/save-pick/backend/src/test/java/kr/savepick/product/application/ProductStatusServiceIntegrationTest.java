package kr.savepick.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductRepository;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.stock.application.StockAdjustService;
import kr.savepick.support.ProductTestFixtures;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-106 (11-api-spec.md §7, BR-025, BR-030, docs/16-test-plan.md TC-075·TC-076).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class ProductStatusServiceIntegrationTest {

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductStatusService productStatusService;

    @Autowired
    private StockAdjustService stockAdjustService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ServerClock serverClock;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private Product registerSample() {
        LocalDateTime now = serverClock.now();
        return productRegisterService.register("상품", "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, 1L);
    }

    private Long registerAdmin() {
        Member admin = Member.registerAdmin(
                "admin-" + java.util.UUID.randomUUID() + "@test.com", "hash", "관리자", "01000000000", serverClock.now());
        return memberRepository.save(admin).getId();
    }

    @Test
    @DisplayName("TC_075a_재고_미등록_DRAFT를_ON_SALE로_전환하면_PRODUCT_STATUS_TRANSITION_DENIED다")
    void TC_075a_재고_미등록_DRAFT를_ON_SALE로_전환하면_PRODUCT_STATUS_TRANSITION_DENIED다() {
        Product product = registerSample();
        Long adminId = registerAdmin();

        assertThatThrownBy(() -> productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_STATUS_TRANSITION_DENIED);
    }

    @Test
    @DisplayName("재고를_등록하면_DRAFT를_ON_SALE로_전환할_수_있다")
    void 재고를_등록하면_DRAFT를_ON_SALE로_전환할_수_있다() {
        Product product = registerSample();
        Long adminId = registerAdmin();
        stockAdjustService.adjust(product.getId(), 10, "초기 등록", adminId);

        ProductStatusService.StatusChangeResult result =
                productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);

        assertThat(result.product().getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("TC_075b_CLOSED_상품을_ON_SALE로_전환하면_PRODUCT_STATUS_TRANSITION_DENIED다")
    void TC_075b_CLOSED_상품을_ON_SALE로_전환하면_PRODUCT_STATUS_TRANSITION_DENIED다() {
        Product product = registerSample();
        Long adminId = registerAdmin();
        stockAdjustService.adjust(product.getId(), 10, null, adminId);
        productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);

        // BATCH-02(상품 마감 상태 전환)는 이번 슬라이스 범위 밖이라 시스템 전이를 직접 흉내낸다.
        Product managed = productRepository.findById(product.getId()).orElseThrow();
        assertThat(managed.closeIfDue(managed.getClosingAt().plusMinutes(1))).isTrue();
        productRepository.save(managed);

        assertThatThrownBy(() -> productStatusService.changeStatus(product.getId(), ProductStatus.HIDDEN, adminId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_STATUS_TRANSITION_DENIED);
    }

    @Test
    @DisplayName("TC_076_HIDDEN_전환_후_다시_ON_SALE로_전환할_수_있다")
    void TC_076_HIDDEN_전환_후_다시_ON_SALE로_전환할_수_있다() {
        Product product = registerSample();
        Long adminId = registerAdmin();
        stockAdjustService.adjust(product.getId(), 10, null, adminId);
        productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);

        ProductStatusService.StatusChangeResult hidden =
                productStatusService.changeStatus(product.getId(), ProductStatus.HIDDEN, adminId);
        assertThat(hidden.product().getStatus()).isEqualTo(ProductStatus.HIDDEN);

        ProductStatusService.StatusChangeResult resumed =
                productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);
        assertThat(resumed.product().getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("존재하지_않는_상품의_상태를_바꾸면_NOT_FOUND다")
    void 존재하지_않는_상품의_상태를_바꾸면_NOT_FOUND다() {
        Long adminId = registerAdmin();
        assertThatThrownBy(() -> productStatusService.changeStatus(999_999L, ProductStatus.ON_SALE, adminId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    /** 이 상품을 담은 확정 주문 1건을 넣는다. 관심사는 "전환이 확정 주문을 어떻게 세는가"뿐이다. */
    private void insertConfirmedOrder(Long productId, String status, int minuteOffset) {
        LocalDateTime now = serverClock.now();
        LocalDateTime pickupStartAt = now.plusHours(2).plusMinutes(minuteOffset);
        Long slotId = jdbcTemplate.queryForObject(
                "INSERT INTO pickup_slots (store_id, slot_date, start_at, end_at, capacity, reserved_count, blocked, created_at) "
                        + "VALUES (1, ?, ?, ?, 20, 1, false, ?) RETURNING id",
                Long.class, java.sql.Date.valueOf(pickupStartAt.toLocalDate()),
                java.sql.Timestamp.valueOf(pickupStartAt), java.sql.Timestamp.valueOf(pickupStartAt.plusMinutes(30)),
                java.sql.Timestamp.valueOf(now));

        Member customer = Member.registerCustomer(
                "customer-" + java.util.UUID.randomUUID() + "@test.com", "hash", "고객", "01011112222", now);
        Long memberId = memberRepository.save(customer).getId();

        String orderNo = "ORD-" + String.format("%015d", System.nanoTime() % 1_000_000_000_000_000L);
        String canceledBy = "CANCELED".equals(status) ? "CUSTOMER" : null;
        Long orderId = jdbcTemplate.queryForObject(
                "INSERT INTO orders (order_no, member_id, status, total_amount, contact_name, contact_phone, "
                        + "hold_expires_at, pickup_slot_id, pickup_business_date, canceled_by) "
                        + "VALUES (?, ?, ?, 10000, '고객', '01011112222', ?, ?, ?, ?) RETURNING id",
                Long.class, orderNo, memberId, status, java.sql.Timestamp.valueOf(now.plusMinutes(10)),
                slotId, java.sql.Date.valueOf(pickupStartAt.toLocalDate()), canceledBy);

        jdbcTemplate.update(
                "INSERT INTO order_items (order_id, product_id, product_name, sale_unit, quantity, "
                        + "original_unit_price, discount_rate, unit_price, line_amount, product_closing_at) "
                        + "VALUES (?, ?, '상품', '1개', 1, 10000, 0, 10000, 10000, ?)",
                orderId, productId, java.sql.Timestamp.valueOf(pickupStartAt));
    }

    @Test
    @DisplayName("HIDDEN_전환은_유지되는_확정_주문_수를_실제로_세어_돌려준다")
    void HIDDEN_전환은_유지되는_확정_주문_수를_실제로_세어_돌려준다() {
        Product product = registerSample();
        Long adminId = registerAdmin();
        stockAdjustService.adjust(product.getId(), 10, null, adminId);
        productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);

        insertConfirmedOrder(product.getId(), "CONFIRMED", 0);
        insertConfirmedOrder(product.getId(), "READY", 30);
        // 종결된 주문은 이 전환으로 영향받을 여지가 없어 세지 않는다(05 §5.2).
        insertConfirmedOrder(product.getId(), "CANCELED", 60);
        insertConfirmedOrder(product.getId(), "NO_SHOW", 90);

        ProductStatusService.StatusChangeResult result =
                productStatusService.changeStatus(product.getId(), ProductStatus.HIDDEN, adminId);

        assertThat(result.keptConfirmedOrderCount()).isEqualTo(2);
    }
}
