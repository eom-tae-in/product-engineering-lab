package kr.savepick.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.cart.application.CartOwner;
import kr.savepick.cart.application.CartService;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.order.domain.OrderRepository;
import kr.savepick.order.domain.OrderStatus;
import kr.savepick.order.infrastructure.OrderStatusHistoryJpaRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-019 (11-api-spec.md §4, BR-008, 05 §2.2).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class OrderAbandonServiceIntegrationTest {

    @Autowired
    private OrderDraftService orderDraftService;

    @Autowired
    private OrderAbandonService orderAbandonService;

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
    private OrderRepository orderRepository;

    @Autowired
    private OrderStatusHistoryJpaRepository orderStatusHistoryJpaRepository;

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
                "포기테스트상품", "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, adminId);
        stockAdjustService.adjust(product.getId(), stock, null, adminId);
        productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);
        return product;
    }

    @Test
    @DisplayName("PENDING_주문서를_포기하면_EXPIRED로_바뀌고_재고가_복구된다")
    void PENDING_주문서를_포기하면_EXPIRED로_바뀌고_재고가_복구된다() {
        Long customerId = registerCustomer();
        Product product = registerOnSaleProduct(10, customerId);
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), 3);
        OrderDraftService.DraftResult draft = orderDraftService.createDraft(customerId, null);

        OrderAbandonService.AbandonResult result = orderAbandonService.abandon(draft.order().getId(), customerId);

        assertThat(result.status()).isEqualTo(OrderStatus.EXPIRED);
        var reloaded = orderRepository.findById(draft.order().getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(reloaded.getExpiredAt()).isNotNull();

        var stock = productStockJpaRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(stock.getHeldQuantity()).isZero();
        assertThat(stock.getAvailableQuantity()).isEqualTo(10);

        assertThat(orderStatusHistoryJpaRepository.findByOrderId(draft.order().getId()))
                .anySatisfy(h -> {
                    assertThat(h.getFromStatus()).isEqualTo(OrderStatus.PENDING);
                    assertThat(h.getToStatus()).isEqualTo(OrderStatus.EXPIRED);
                });
    }

    @Test
    @DisplayName("PENDING이_아닌_주문은_포기할_수_없다")
    void PENDING이_아닌_주문은_포기할_수_없다() {
        Long customerId = registerCustomer();
        Product product = registerOnSaleProduct(10, customerId);
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), 1);
        OrderDraftService.DraftResult draft = orderDraftService.createDraft(customerId, null);
        orderAbandonService.abandon(draft.order().getId(), customerId);

        assertThatThrownBy(() -> orderAbandonService.abandon(draft.order().getId(), customerId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("남의_주문을_포기하려_하면_404다")
    void 남의_주문을_포기하려_하면_404다() {
        Long owner = registerCustomer();
        Long stranger = registerCustomer();
        Product product = registerOnSaleProduct(10, owner);
        cartService.addItem(CartOwner.ofMember(owner), product.getId(), 1);
        OrderDraftService.DraftResult draft = orderDraftService.createDraft(owner, null);

        assertThatThrownBy(() -> orderAbandonService.abandon(draft.order().getId(), stranger))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
