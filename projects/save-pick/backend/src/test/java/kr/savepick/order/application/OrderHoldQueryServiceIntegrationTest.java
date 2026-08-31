package kr.savepick.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.UUID;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.cart.application.CartOwner;
import kr.savepick.cart.application.CartService;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.order.domain.OrderStatus;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.application.ProductStatusService;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.stock.application.StockAdjustService;
import kr.savepick.stock.infrastructure.ProductStockJpaRepository;
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
 * API-018 (11-api-spec.md §4, docs/16-test-plan.md TC-031, BR-007·008, 13번 §2 (e)).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class OrderHoldQueryServiceIntegrationTest {

    @Autowired
    private OrderDraftService orderDraftService;

    @Autowired
    private OrderHoldQueryService orderHoldQueryService;

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ServerClock serverClock;

    private Long registerCustomer() {
        Member customer = Member.registerCustomer(
                "customer-" + UUID.randomUUID() + "@test.com", "hash", "고객", "01011112222", serverClock.now());
        return memberRepository.save(customer).getId();
    }

    private Product registerOnSaleProduct(int stock, Long adminId) {
        LocalDateTime now = serverClock.now();
        Product product = productRegisterService.register(
                "선점조회상품", "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, adminId);
        stockAdjustService.adjust(product.getId(), stock, null, adminId);
        productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);
        return product;
    }

    /**
     * 주문서 생성 직후의 {@code Order}는 이미 영속성 컨텍스트(1차 캐시)에 관리되고 있다. JDBC로
     * DB 행을 직접 바꾼 뒤에는 {@code entityManager.clear()}로 캐시를 비워야 이후 조회가
     * 갱신된 값을 읽는다(그렇지 않으면 캐시된 옛 엔티티를 그대로 돌려받는다).
     */
    private void setHoldExpiresAt(Long orderId, LocalDateTime holdExpiresAt) {
        jdbcTemplate.update("UPDATE orders SET hold_expires_at = ? WHERE id = ?", Timestamp.valueOf(holdExpiresAt), orderId);
        entityManager.clear();
    }

    @Test
    @DisplayName("TC_031_잔여_시간이_60초_이내면_expiringSoon이_true다")
    void TC_031_잔여_시간이_60초_이내면_expiringSoon이_true다() {
        Long customerId = registerCustomer();
        Product product = registerOnSaleProduct(10, customerId);
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), 1);
        OrderDraftService.DraftResult draft = orderDraftService.createDraft(customerId, null);
        // 59초 같은 1초 단위 경계값은 테스트 실행 중 실제로 흐르는 처리 시간(JDBC 왕복 등)에
        // 쉽게 잠식돼 불안정해진다 — 임계값(60초)보다 확실히 작은 값으로 판정 로직만 검증한다.
        setHoldExpiresAt(draft.order().getId(), serverClock.now().plusSeconds(30));

        OrderHoldQueryService.HoldStatusResult result = orderHoldQueryService.getHoldStatus(draft.order().getId(), customerId);

        assertThat(result.expiringSoon()).isTrue();
        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("잔여_시간이_60초를_넉넉히_넘으면_expiringSoon이_false다")
    void 잔여_시간이_60초를_넉넉히_넘으면_expiringSoon이_false다() {
        Long customerId = registerCustomer();
        Product product = registerOnSaleProduct(10, customerId);
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), 1);
        OrderDraftService.DraftResult draft = orderDraftService.createDraft(customerId, null);
        setHoldExpiresAt(draft.order().getId(), serverClock.now().plusSeconds(120));

        OrderHoldQueryService.HoldStatusResult result = orderHoldQueryService.getHoldStatus(draft.order().getId(), customerId);

        assertThat(result.expiringSoon()).isFalse();
    }

    @Test
    @DisplayName("만료된_주문을_조회하면_조회_시점에_EXPIRED로_종결되고_재고가_복구된다")
    void 만료된_주문을_조회하면_조회_시점에_EXPIRED로_종결되고_재고가_복구된다() {
        Long customerId = registerCustomer();
        Product product = registerOnSaleProduct(10, customerId);
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), 4);
        OrderDraftService.DraftResult draft = orderDraftService.createDraft(customerId, null);
        setHoldExpiresAt(draft.order().getId(), serverClock.now().minusSeconds(1));

        OrderHoldQueryService.HoldStatusResult result = orderHoldQueryService.getHoldStatus(draft.order().getId(), customerId);

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(result.holdRemainingSeconds()).isZero();
        var stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(stock.getHeldQuantity()).isZero();
        assertThat(stock.getAvailableQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("남의_주문의_선점_상태를_조회하면_404다")
    void 남의_주문의_선점_상태를_조회하면_404다() {
        Long owner = registerCustomer();
        Long stranger = registerCustomer();
        Product product = registerOnSaleProduct(10, owner);
        cartService.addItem(CartOwner.ofMember(owner), product.getId(), 1);
        OrderDraftService.DraftResult draft = orderDraftService.createDraft(owner, null);

        assertThatThrownBy(() -> orderHoldQueryService.getHoldStatus(draft.order().getId(), stranger))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
