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
import kr.savepick.product.infrastructure.ProductChangeLogJpaRepository;
import kr.savepick.support.ProductTestFixtures;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-105 (11-api-spec.md §7, BR-003, BR-005, docs/16-test-plan.md TC-073·TC-074).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class ProductUpdateServiceIntegrationTest {

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private ProductUpdateService productUpdateService;

    @Autowired
    private ProductChangeLogJpaRepository productChangeLogJpaRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ServerClock serverClock;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private kr.savepick.store.domain.StoreRepository storeRepository;

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
    @DisplayName("TC_073_정가를_수정하면_반영되고_이력이_남는다")
    void TC_073_정가를_수정하면_반영되고_이력이_남는다() {
        Product product = registerSample();
        Long adminId = registerAdmin();

        ProductUpdateService.UpdateResult result =
                productUpdateService.update(product.getId(), null, null, null, 11000, null, null, false, adminId);

        assertThat(result.product().getOriginalPrice()).isEqualTo(11000);
        assertThat(result.changedFields()).containsExactly("originalPrice");
        var logs = productChangeLogJpaRepository.findByProductIdOrderByChangedAtDesc(product.getId(), PageRequest.of(0, 10));
        assertThat(logs.getContent()).anySatisfy(log -> {
            assertThat(log.getChangedField()).isEqualTo("original_price");
            assertThat(log.getBeforeValue()).isEqualTo("10000");
            assertThat(log.getAfterValue()).isEqualTo("11000");
        });
    }

    @Test
    @DisplayName("TC_074_마감_시각을_단축해도_원장에_남고_저장된다")
    void TC_074_마감_시각을_단축해도_원장에_남고_저장된다() {
        Product product = registerSample();
        Long adminId = registerAdmin();
        LocalDateTime shortened = product.getClosingAt().minusHours(1);

        ProductUpdateService.UpdateResult result =
                productUpdateService.update(product.getId(), null, null, null, null, shortened, null, false, adminId);

        assertThat(result.product().getClosingAt()).isEqualTo(shortened);
        assertThat(result.affectedConfirmedOrderCount()).isZero();
    }

    @Test
    @DisplayName("마감_시각을_영업_종료_이후로_옮기면_CLOSING_TIME_INVALID다")
    void 마감_시각을_영업_종료_이후로_옮기면_CLOSING_TIME_INVALID다() {
        Product product = registerSample();
        LocalDateTime invalid = serverClock.now().toLocalDate().atTime(23, 0);

        assertThatThrownBy(() -> productUpdateService.update(product.getId(), null, null, null, null, invalid, null, false, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CLOSING_TIME_INVALID);
    }

    @Test
    @DisplayName("존재하지_않는_상품을_수정하면_NOT_FOUND다")
    void 존재하지_않는_상품을_수정하면_NOT_FOUND다() {
        assertThatThrownBy(() -> productUpdateService.update(999_999L, "새 이름", null, null, null, null, null, false, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    /**
     * 확정 주문 1건을 만든다. 픽업 시간대는 {@code pickupStartAt}에 두고, 상품은 주문 품목으로 건다.
     * order 도메인 서비스를 거치지 않고 직접 넣는 이유는 이 테스트의 관심이 "마감 단축이 확정
     * 주문을 어떻게 세는가"에만 있기 때문이다.
     */
    private void insertConfirmedOrder(Long productId, LocalDateTime pickupStartAt, String status) {
        LocalDateTime now = serverClock.now();
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
        // CHK_orders_canceled_requires_actor — CANCELED는 취소 주체가 있어야 한다(BR-020).
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
    @DisplayName("마감을_앞당겨_확정_주문의_픽업보다_빨라지면_동의_없이는_거부하고_영향_건수를_알려준다")
    void 마감을_앞당겨_확정_주문의_픽업보다_빨라지면_동의_없이는_거부하고_영향_건수를_알려준다() {
        Product product = registerSample();
        Long adminId = registerAdmin();
        LocalDateTime originalClosing = product.getClosingAt();
        // 픽업이 새 마감(1시간 앞당김)보다 늦은 확정 주문 2건 + 그보다 이른 1건
        insertConfirmedOrder(product.getId(), originalClosing.minusMinutes(30), "CONFIRMED");
        insertConfirmedOrder(product.getId(), originalClosing.minusMinutes(15), "READY");
        insertConfirmedOrder(product.getId(), originalClosing.minusHours(2), "CONFIRMED");

        LocalDateTime shortened = originalClosing.minusHours(1);

        // 11번 API-105 — confirmEarlierClosing 없이는 실행하지 않고 영향 건수를 담아 거부한다.
        assertThatThrownBy(() -> productUpdateService.update(
                        product.getId(), null, null, null, null, shortened, null, false, adminId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(be.details()).containsEntry("affectedConfirmedOrderCount", 2);
                });
    }

    @Test
    @DisplayName("동의하면_마감_단축이_실행되고_영향_건수를_결과로_돌려준다")
    void 동의하면_마감_단축이_실행되고_영향_건수를_결과로_돌려준다() {
        Product product = registerSample();
        Long adminId = registerAdmin();
        LocalDateTime originalClosing = product.getClosingAt();
        insertConfirmedOrder(product.getId(), originalClosing.minusMinutes(30), "CONFIRMED");
        LocalDateTime shortened = originalClosing.minusHours(1);

        ProductUpdateService.UpdateResult result = productUpdateService.update(
                product.getId(), null, null, null, null, shortened, null, true, adminId);

        assertThat(result.product().getClosingAt()).isEqualTo(shortened);
        assertThat(result.affectedConfirmedOrderCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("취소_노쇼_완료_주문은_마감_단축_영향_건수에_세지_않는다")
    void 취소_노쇼_완료_주문은_마감_단축_영향_건수에_세지_않는다() {
        Product product = registerSample();
        Long adminId = registerAdmin();
        LocalDateTime originalClosing = product.getClosingAt();
        // pickup_slots는 UNIQUE(store_id, start_at)이라 주문마다 시각을 다르게 둔다.
        insertConfirmedOrder(product.getId(), originalClosing.minusMinutes(30), "CANCELED");
        insertConfirmedOrder(product.getId(), originalClosing.minusMinutes(20), "NO_SHOW");
        insertConfirmedOrder(product.getId(), originalClosing.minusMinutes(10), "COMPLETED");

        ProductUpdateService.UpdateResult result = productUpdateService.update(
                product.getId(), null, null, null, null, originalClosing.minusHours(1), null, false, adminId);

        assertThat(result.affectedConfirmedOrderCount()).isZero();
    }
}
