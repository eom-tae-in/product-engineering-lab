package kr.savepick.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.cart.application.CartOwner;
import kr.savepick.cart.application.CartService;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.order.domain.OrderItem;
import kr.savepick.order.domain.OrderStatus;
import kr.savepick.order.domain.OrderStatusHistory;
import kr.savepick.order.infrastructure.OrderStatusHistoryJpaRepository;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.application.ProductStatusService;
import kr.savepick.product.domain.Product;
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
 * API-017 (11-api-spec.md §4, docs/16-test-plan.md TC-027~030·034, BR-005·007·009·010·023·027).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class OrderDraftServiceIntegrationTest {

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
    private OrderStatusHistoryJpaRepository orderStatusHistoryJpaRepository;

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

    private Long registerAdmin() {
        Member admin = Member.registerAdmin("admin-" + UUID.randomUUID() + "@test.com", "hash", "관리자", "01000000000", serverClock.now());
        return memberRepository.save(admin).getId();
    }

    /** DRAFT로 등록 후 재고를 넣고 ON_SALE로 전환한다(재고 등록이 DRAFT→ON_SALE 전이 조건이다). */
    private Product registerOnSaleProduct(String name, int price, int stock, int maxOrderQuantity, Long adminId) {
        LocalDateTime now = serverClock.now();
        Product product = productRegisterService.register(name, "설명", "1개", price, ProductTestFixtures.futureClosingAt(now, 5), (short) maxOrderQuantity, adminId);
        stockAdjustService.adjust(product.getId(), stock, null, adminId);
        productStatusService.changeStatus(product.getId(), kr.savepick.product.domain.ProductStatus.ON_SALE, adminId);
        return product;
    }

    private void addToCart(Long memberId, Long productId, int quantity) {
        cartService.addItem(CartOwner.ofMember(memberId), productId, quantity);
    }

    @Test
    @DisplayName("TC_027_주문서_생성_시_전_품목이_선점되고_금액이_스냅샷되고_이력이_남는다")
    void TC_027_주문서_생성_시_전_품목이_선점되고_금액이_스냅샷되고_이력이_남는다() {
        Long adminId = registerAdmin();
        Long customerId = registerCustomer();
        Product apple = registerOnSaleProduct("사과", 10000, 10, 5, adminId);
        Product pear = registerOnSaleProduct("배", 5000, 10, 5, adminId);
        addToCart(customerId, apple.getId(), 2);
        addToCart(customerId, pear.getId(), 1);

        OrderDraftService.DraftResult result = orderDraftService.createDraft(customerId, null);

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.order().getOrderNo()).startsWith("ORD-");
        assertThat(result.order().getHoldExpiresAt()).isNotNull();
        assertThat(result.items()).hasSize(2);
        int expectedTotal = result.items().stream().mapToInt(OrderItem::getLineAmount).sum();
        assertThat(result.order().getTotalAmount()).isEqualTo(expectedTotal);

        var stock = productStockJpaRepository.findByProductId(apple.getId()).orElseThrow();
        assertThat(stock.getHeldQuantity()).isEqualTo(2);
        assertThat(stock.getAvailableQuantity()).isEqualTo(8);

        List<OrderStatusHistory> histories = orderStatusHistoryJpaRepository.findByOrderId(result.order().getId());
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getFromStatus()).isNull();
        assertThat(histories.get(0).getToStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("TC_028_한_품목이라도_재고가_부족하면_전체_선점이_실패하고_주문서도_만들어지지_않는다")
    void TC_028_한_품목이라도_재고가_부족하면_전체_선점이_실패하고_주문서도_만들어지지_않는다() {
        Long adminId = registerAdmin();
        Long customerId = registerCustomer();
        Product plenty = registerOnSaleProduct("여유상품", 10000, 10, 5, adminId);
        // 장바구니 담기 시점에는 재고가 충분해야 CartService의 담기 자체가 막히지 않는다(BR-010 —
        // 담기는 재고를 선점하지 않으므로 담은 뒤에도 다른 고객이 재고를 가져갈 수 있다).
        Product scarce = registerOnSaleProduct("품절임박상품", 10000, 5, 5, adminId);
        addToCart(customerId, plenty.getId(), 2);
        addToCart(customerId, scarce.getId(), 5);
        // 담은 뒤 다른 경로(예: 다른 고객의 구매)로 재고가 줄어든 상황을 재현한다.
        stockAdjustService.adjust(scarce.getId(), 1, null, adminId);

        assertThatThrownBy(() -> orderDraftService.createDraft(customerId, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.OUT_OF_STOCK);

        // InventoryHoldService는 전 품목을 잠근 뒤 전량 검증부터 하므로, 하나라도 부족하면
        // plenty를 포함해 어떤 품목도 실제로 선점(쓰기)되지 않는다(BR-027, 부분 선점 없음).
        var plentyStock = productStockJpaRepository.findByProductId(plenty.getId()).orElseThrow();
        assertThat(plentyStock.getHeldQuantity()).isZero();
        // 주문서(orders) 행 자체가 롤백되어 남지 않는지는 이 테스트 메서드의 트랜잭션
        // 안에서는 관찰할 수 없다(테스트 트랜잭션에 합류한 채 예외만 던지면 즉시 물리
        // 롤백되지 않는다) — 이 보장은 OrderApiIntegrationTest에서 실제 HTTP 요청 단위로
        // (요청마다 독립된 트랜잭션) 검증한다.
    }

    @Test
    @DisplayName("TC_029_유효한_PENDING_주문서가_있으면_새로_만들_수_없다")
    void TC_029_유효한_PENDING_주문서가_있으면_새로_만들_수_없다() {
        Long adminId = registerAdmin();
        Long customerId = registerCustomer();
        Product product = registerOnSaleProduct("중복주문상품", 10000, 10, 5, adminId);
        addToCart(customerId, product.getId(), 1);
        OrderDraftService.DraftResult first = orderDraftService.createDraft(customerId, null);

        Product another = registerOnSaleProduct("두번째상품", 5000, 10, 5, adminId);
        addToCart(customerId, another.getId(), 1);

        assertThatThrownBy(() -> orderDraftService.createDraft(customerId, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.errorCode()).isEqualTo(ErrorCode.PENDING_ORDER_EXISTS);
                    assertThat(be.details()).containsEntry("orderId", first.order().getId());
                });
    }

    @Test
    @DisplayName("TC_030_노쇼_제한_계정은_주문서를_만들_수_없다")
    void TC_030_노쇼_제한_계정은_주문서를_만들_수_없다() {
        Long adminId = registerAdmin();
        Long customerId = registerCustomer();
        Product product = registerOnSaleProduct("제한계정상품", 10000, 10, 5, adminId);
        addToCart(customerId, product.getId(), 1);
        // 실제로는 BATCH-03(NoShowDetectionJob → OrderRestrictionService.applyNoShowRestriction)이
        // member_restrictions 행을 만든다. 판정은 이 행의 ends_at을 조회 시점에 다시 계산하므로
        // (OrderRestrictionService 주석 참고), members.order_permission 컬럼만 바꾸는 것으로는
        // 더 이상 차단되지 않는다 — 활성 제한 행을 함께 만들어야 한다.
        Long dummyTriggerOrderId = jdbcTemplate.queryForObject(
                "INSERT INTO orders (order_no, member_id, status, total_amount, contact_name, contact_phone, "
                        + "no_show_at, created_at, updated_at) "
                        + "VALUES (?, ?, 'NO_SHOW', 0, '고객', '01011112222', ?, ?, ?) RETURNING id",
                Long.class, "ORD-DUMMY-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                customerId, serverClock.now(), serverClock.now(), serverClock.now());
        jdbcTemplate.update(
                "INSERT INTO member_restrictions (member_id, reason, trigger_order_id, triggered_no_show_count, started_at, ends_at) "
                        + "VALUES (?, 'NO_SHOW_ACCUMULATION', ?, 3, ?, ?)",
                customerId, dummyTriggerOrderId, serverClock.now(), serverClock.now().plusDays(7));
        jdbcTemplate.update("UPDATE members SET order_permission = 'RESTRICTED' WHERE id = ?", customerId);
        // Member 엔티티가 이미 영속성 컨텍스트에 있어(registerCustomer의 save) 캐시를 비워야
        // 이후 조회가 방금 바꾼 값을 읽는다.
        entityManager.clear();

        assertThatThrownBy(() -> orderDraftService.createDraft(customerId, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.ORDER_RESTRICTED);
    }

    @Test
    @DisplayName("빈_장바구니로는_주문서를_만들_수_없다")
    void 빈_장바구니로는_주문서를_만들_수_없다() {
        Long customerId = registerCustomer();

        assertThatThrownBy(() -> orderDraftService.createDraft(customerId, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CART_EMPTY);
    }

    @Test
    @DisplayName("cartItemIds를_지정하면_지정한_품목만_대상이_된다")
    void cartItemIds를_지정하면_지정한_품목만_대상이_된다() {
        Long adminId = registerAdmin();
        Long customerId = registerCustomer();
        Product apple = registerOnSaleProduct("사과2", 10000, 10, 5, adminId);
        Product pear = registerOnSaleProduct("배2", 5000, 10, 5, adminId);
        CartService.AddItemResult appleItem = cartService.addItem(CartOwner.ofMember(customerId), apple.getId(), 1);
        cartService.addItem(CartOwner.ofMember(customerId), pear.getId(), 1);

        OrderDraftService.DraftResult result = orderDraftService.createDraft(customerId, List.of(appleItem.cartItemId()));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getProductId()).isEqualTo(apple.getId());
    }
}
