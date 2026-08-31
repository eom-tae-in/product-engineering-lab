package kr.savepick.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.cart.application.CartOwner;
import kr.savepick.cart.application.CartService;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.order.domain.OrderStatus;
import kr.savepick.order.payment.PaymentAttemptService;
import kr.savepick.pickup.application.PickupSlotProvisionService;
import kr.savepick.pickup.application.PickupSlotQueryService;
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

/** API-115·API-116 (11-api-spec.md §9, docs/16-test-plan.md TC-090~092). */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class OrderFulfillServiceIntegrationTest {

    @Autowired
    private OrderDraftService orderDraftService;

    @Autowired
    private PickupSlotAssignService pickupSlotAssignService;

    @Autowired
    private PickupSlotProvisionService pickupSlotProvisionService;

    @Autowired
    private PaymentAttemptService paymentAttemptService;

    @Autowired
    private OrderFulfillService orderFulfillService;

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

    @Autowired
    private EntityManager entityManager;

    private Long registerCustomer(String tag) {
        Member customer = Member.registerCustomer(
                tag + "-" + UUID.randomUUID() + "@test.com", "hash", "고객", "01011112222", serverClock.now());
        return memberRepository.save(customer).getId();
    }

    private Product registerOnSaleProduct(String name, int stock, Long adminId) {
        LocalDateTime now = serverClock.now();
        Product product = productRegisterService.register(
                name, "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, adminId);
        stockAdjustService.adjust(product.getId(), stock, null, adminId);
        productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);
        return product;
    }

    private Long confirmOrder(Long customerId, Product product) {
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), 1);
        var draft = orderDraftService.createDraft(customerId, null);
        LocalDate today = serverClock.now().toLocalDate();
        pickupSlotProvisionService.provisionForDate(today, serverClock.now());
        pickupSlotProvisionService.provisionForDate(today.plusDays(1), serverClock.now());
        PickupSlotQueryService.SelectableSlotsResult slots =
                pickupSlotAssignService.getSelectableSlots(draft.order().getId(), customerId, null);
        var selectable = slots.slots().stream().filter(PickupSlotQueryService.SlotView::selectable).findFirst().orElseThrow();
        pickupSlotAssignService.assign(draft.order().getId(), customerId, selectable.slotId());
        var result = paymentAttemptService.pay(draft.order().getId(), customerId, draft.order().getTotalAmount(), UUID.randomUUID().toString());
        entityManager.clear();
        return result.orderId();
    }

    @Test
    @DisplayName("TC_090_CONFIRMED_주문은_준비완료로_전환되고_재고는_불변한다")
    void TC_090_CONFIRMED_주문은_준비완료로_전환되고_재고는_불변한다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("준비완료상품", 10, adminId);
        Long orderId = confirmOrder(customerId, product);
        var stockBefore = productStockJpaRepository.findByProductId(product.getId()).orElseThrow().getConfirmedQuantity();

        OrderFulfillService.FulfillResult result = orderFulfillService.markReady(orderId, adminId);

        assertThat(result.status()).isEqualTo(OrderStatus.READY);
        assertThat(productStockJpaRepository.findByProductId(product.getId()).orElseThrow().getConfirmedQuantity()).isEqualTo(stockBefore);
    }

    @Test
    @DisplayName("READY나_COMPLETED_주문에_준비완료를_다시_요청하면_INVALID_ORDER_STATUS다")
    void READY나_COMPLETED_주문에_준비완료를_다시_요청하면_INVALID_ORDER_STATUS다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("준비완료중복상품", 10, adminId);
        Long orderId = confirmOrder(customerId, product);
        orderFulfillService.markReady(orderId, adminId);

        assertThatThrownBy(() -> orderFulfillService.markReady(orderId, adminId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("TC_091_CONFIRMED_또는_READY는_완료처리되고_중복_완료는_거부된다")
    void TC_091_CONFIRMED_또는_READY는_완료처리되고_중복_완료는_거부된다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("완료처리상품", 10, adminId);
        Long orderId = confirmOrder(customerId, product);

        OrderFulfillService.FulfillResult result = orderFulfillService.complete(orderId, adminId);
        assertThat(result.status()).isEqualTo(OrderStatus.COMPLETED);

        assertThatThrownBy(() -> orderFulfillService.complete(orderId, adminId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("READY_경유_후에도_완료처리할_수_있다")
    void READY_경유_후에도_완료처리할_수_있다() {
        Long adminId = registerCustomer("admin");
        Long customerId = registerCustomer("customer");
        Product product = registerOnSaleProduct("READY경유완료상품", 10, adminId);
        Long orderId = confirmOrder(customerId, product);
        orderFulfillService.markReady(orderId, adminId);

        OrderFulfillService.FulfillResult result = orderFulfillService.complete(orderId, adminId);

        assertThat(result.status()).isEqualTo(OrderStatus.COMPLETED);
    }
}
