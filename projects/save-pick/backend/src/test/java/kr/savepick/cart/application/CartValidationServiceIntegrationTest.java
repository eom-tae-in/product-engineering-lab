package kr.savepick.cart.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.cart.application.CartValidationService.CartItemEvaluation;
import kr.savepick.cart.application.CartValidationService.CartView;
import kr.savepick.cart.domain.CartItem;
import kr.savepick.cart.domain.CartItemRepository;
import kr.savepick.cart.domain.UnavailableReason;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.application.ProductStatusService;
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
 * API-012 (11-api-spec.md §3, BR-005, BR-006, BR-030, docs/16-test-plan.md TC-024·TC-025).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class CartValidationServiceIntegrationTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private CartValidationService cartValidationService;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private ProductStatusService productStatusService;

    @Autowired
    private StockAdjustService stockAdjustService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ServerClock serverClock;

    private Long registerAdmin() {
        Member admin = Member.registerAdmin(
                "admin-" + UUID.randomUUID() + "@test.com", "hash", "관리자", "01000000000", serverClock.now());
        return memberRepository.save(admin).getId();
    }

    private Product onSaleProduct(String name, int totalQuantity, Long adminId) {
        LocalDateTime now = serverClock.now();
        Product product = productRegisterService.register(
                name, "설명", "1개", 12000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, adminId);
        stockAdjustService.adjust(product.getId(), totalQuantity, null, adminId);
        productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);
        return product;
    }

    @Test
    @DisplayName("TC_024_담은_뒤_가격이_바뀌면_priceChanged가_true다")
    void TC_024_담은_뒤_가격이_바뀌면_priceChanged가_true다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("가격변동상품", 10, adminId);
        CartOwner guest = CartOwner.ofGuest(UUID.randomUUID());
        CartService.AddItemResult added = cartService.addItem(guest, product.getId(), 1);

        // 담은 시점 가격을 인위적으로 다른 값으로 바꿔 "그 사이 할인 구간이 바뀐" 상황을 흉내낸다.
        CartItem item = cartItemRepository.findById(added.cartItemId()).orElseThrow();
        item.changeQuantity(item.getQuantity(), item.getAddedPrice() + 1000, serverClock.now());
        cartItemRepository.save(item);

        CartView view = cartValidationService.getCart(guest);

        assertThat(view.items()).hasSize(1);
        CartItemEvaluation evaluation = view.items().get(0);
        assertThat(evaluation.priceChanged()).isTrue();
        assertThat(evaluation.currentPrice()).isNotEqualTo(evaluation.addedPrice());
    }

    @Test
    @DisplayName("TC_024_담은_수량보다_잔여_수량이_적어지면_shortage와_OUT_OF_STOCK을_함께_반환한다")
    void TC_024_담은_수량보다_잔여_수량이_적어지면_shortage와_OUT_OF_STOCK을_함께_반환한다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("부족상품", 5, adminId);
        CartOwner guest = CartOwner.ofGuest(UUID.randomUUID());
        cartService.addItem(guest, product.getId(), 3);
        stockAdjustService.adjust(product.getId(), 1, "재고 축소", adminId);

        CartView view = cartValidationService.getCart(guest);

        CartItemEvaluation evaluation = view.items().get(0);
        assertThat(evaluation.shortage()).isEqualTo(2);
        assertThat(evaluation.purchasable()).isFalse();
        assertThat(evaluation.unavailableReason()).isEqualTo(UnavailableReason.OUT_OF_STOCK);
        assertThat(view.orderable()).isFalse();
    }

    @Test
    @DisplayName("TC_025_마감된_상품은_PRODUCT_CLOSED_사유로_구매_불가다")
    void TC_025_마감된_상품은_PRODUCT_CLOSED_사유로_구매_불가다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("마감예정상품", 10, adminId);
        CartOwner guest = CartOwner.ofGuest(UUID.randomUUID());
        cartService.addItem(guest, product.getId(), 1);

        // BATCH-02(상품 마감 상태 전환)는 이번 슬라이스 범위 밖이라 시스템 전이를 직접 흉내낸다
        // (product/application/ProductStatusServiceIntegrationTest와 같은 방식).
        Product managed = productRepository.findById(product.getId()).orElseThrow();
        managed.closeIfDue(managed.getClosingAt().plusMinutes(1));
        productRepository.save(managed);

        CartView view = cartValidationService.getCart(guest);

        CartItemEvaluation evaluation = view.items().get(0);
        assertThat(evaluation.purchasable()).isFalse();
        assertThat(evaluation.unavailableReason()).isEqualTo(UnavailableReason.PRODUCT_CLOSED);
    }

    @Test
    @DisplayName("TC_025_숨김_처리된_상품은_PRODUCT_NOT_ON_SALE_사유로_구매_불가다")
    void TC_025_숨김_처리된_상품은_PRODUCT_NOT_ON_SALE_사유로_구매_불가다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("숨김상품", 10, adminId);
        CartOwner guest = CartOwner.ofGuest(UUID.randomUUID());
        cartService.addItem(guest, product.getId(), 1);
        productStatusService.changeStatus(product.getId(), ProductStatus.HIDDEN, adminId);

        CartView view = cartValidationService.getCart(guest);

        CartItemEvaluation evaluation = view.items().get(0);
        assertThat(evaluation.purchasable()).isFalse();
        assertThat(evaluation.unavailableReason()).isEqualTo(UnavailableReason.PRODUCT_NOT_ON_SALE);
    }

    @Test
    @DisplayName("장바구니가_없어도_오류가_아니라_빈_값을_반환한다")
    void 장바구니가_없어도_오류가_아니라_빈_값을_반환한다() {
        UUID guestToken = UUID.randomUUID();
        CartView view = cartValidationService.getCart(CartOwner.ofGuest(guestToken));

        assertThat(view.items()).isEmpty();
        assertThat(view.totalAmount()).isZero();
        assertThat(view.orderable()).isFalse();
        assertThat(view.guestToken()).isEqualTo(guestToken);
    }
}
