package kr.savepick.cart.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.application.ProductStatusService;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.stock.application.StockAdjustService;
import kr.savepick.stock.application.StockQueryService;
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
 * API-013~016 (11-api-spec.md §3, BR-009, BR-010, docs/16-test-plan.md TC-021~023, TC-025).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class CartServiceIntegrationTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private ProductStatusService productStatusService;

    @Autowired
    private StockAdjustService stockAdjustService;

    @Autowired
    private StockQueryService stockQueryService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ServerClock serverClock;

    private Long registerAdmin() {
        Member admin = Member.registerAdmin(
                "admin-" + UUID.randomUUID() + "@test.com", "hash", "관리자", "01000000000", serverClock.now());
        return memberRepository.save(admin).getId();
    }

    private Product onSaleProduct(String name, int totalQuantity, short maxOrderQuantity, Long adminId) {
        LocalDateTime now = serverClock.now();
        Product product = productRegisterService.register(
                name, "설명", "1개", 12000, ProductTestFixtures.futureClosingAt(now, 5), maxOrderQuantity, adminId);
        stockAdjustService.adjust(product.getId(), totalQuantity, null, adminId);
        productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);
        return product;
    }

    @Test
    @DisplayName("TC_021a_최대_주문_수량을_초과하면_MAX_QUANTITY_EXCEEDED다")
    void TC_021a_최대_주문_수량을_초과하면_MAX_QUANTITY_EXCEEDED다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("한도상품", 20, (short) 5, adminId);
        CartOwner guest = CartOwner.ofGuest(UUID.randomUUID());

        assertThatThrownBy(() -> cartService.addItem(guest, product.getId(), 6))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.MAX_QUANTITY_EXCEEDED);
    }

    @Test
    @DisplayName("TC_021b_11번째_품목을_담으면_CART_ITEM_LIMIT_EXCEEDED다")
    void TC_021b_11번째_품목을_담으면_CART_ITEM_LIMIT_EXCEEDED다() {
        Long adminId = registerAdmin();
        CartOwner guest = CartOwner.ofGuest(UUID.randomUUID());
        for (int i = 0; i < 10; i++) {
            Product product = onSaleProduct("품목" + i, 20, (short) 5, adminId);
            cartService.addItem(guest, product.getId(), 1);
        }
        Product eleventh = onSaleProduct("11번째품목", 20, (short) 5, adminId);

        assertThatThrownBy(() -> cartService.addItem(guest, eleventh.getId(), 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.CART_ITEM_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("같은_상품을_다시_담으면_수량이_합산된다")
    void 같은_상품을_다시_담으면_수량이_합산된다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("합산상품", 20, (short) 5, adminId);
        CartOwner guest = CartOwner.ofGuest(UUID.randomUUID());

        CartService.AddItemResult first = cartService.addItem(guest, product.getId(), 2);
        CartService.AddItemResult second = cartService.addItem(guest, product.getId(), 1);

        assertThat(second.cartItemId()).isEqualTo(first.cartItemId());
        assertThat(second.quantity()).isEqualTo(3);
        assertThat(second.cartItemCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC_022_담기는_다른_사용자의_판매_가능_수량을_줄이지_않는다")
    void TC_022_담기는_다른_사용자의_판매_가능_수량을_줄이지_않는다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("미선점상품", 10, (short) 5, adminId);
        int before = stockQueryService.getCorrectedQuantity(product.getId()).orElseThrow().available();

        cartService.addItem(CartOwner.ofGuest(UUID.randomUUID()), product.getId(), 3);

        int after = stockQueryService.getCorrectedQuantity(product.getId()).orElseThrow().available();
        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("TC_023_수량을_0으로_변경하면_품목이_삭제된다")
    void TC_023_수량을_0으로_변경하면_품목이_삭제된다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("삭제상품", 20, (short) 5, adminId);
        CartOwner guest = CartOwner.ofGuest(UUID.randomUUID());
        CartService.AddItemResult added = cartService.addItem(guest, product.getId(), 2);

        CartService.ChangeQuantityResult result = cartService.changeQuantity(guest, added.cartItemId(), 0);

        assertThat(result.deleted()).isTrue();
        assertThatThrownBy(() -> cartService.removeItem(guest, added.cartItemId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("남의_장바구니_품목을_수정하면_NOT_FOUND다")
    void 남의_장바구니_품목을_수정하면_NOT_FOUND다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("타인상품", 20, (short) 5, adminId);
        CartOwner owner = CartOwner.ofGuest(UUID.randomUUID());
        CartOwner stranger = CartOwner.ofGuest(UUID.randomUUID());
        CartService.AddItemResult added = cartService.addItem(owner, product.getId(), 1);

        assertThatThrownBy(() -> cartService.changeQuantity(stranger, added.cartItemId(), 2))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        assertThatThrownBy(() -> cartService.removeItem(stranger, added.cartItemId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("TC_025_구매_불가_품목만_일괄_삭제되고_나머지는_주문_가능해진다")
    void TC_025_구매_불가_품목만_일괄_삭제되고_나머지는_주문_가능해진다() {
        Long adminId = registerAdmin();
        Product available = onSaleProduct("정상상품", 20, (short) 5, adminId);
        Product shortageProduct = onSaleProduct("품절상품", 5, (short) 5, adminId);
        CartOwner guest = CartOwner.ofGuest(UUID.randomUUID());

        cartService.addItem(guest, available.getId(), 1);
        CartService.AddItemResult shortageItem = cartService.addItem(guest, shortageProduct.getId(), 3);
        // 담은 뒤 재고가 줄어 부족해진 상황을 흉내낸다 (BR-006).
        stockAdjustService.adjust(shortageProduct.getId(), 1, "재고 축소", adminId);

        CartService.RemoveUnavailableResult result = cartService.removeUnavailableItems(guest);

        assertThat(result.removedCartItemIds()).containsExactly(shortageItem.cartItemId());
        assertThat(result.remainingItemCount()).isEqualTo(1);
        assertThat(result.orderable()).isTrue();
    }

    @Test
    @DisplayName("수량_변경_시_판매_가능_수량을_초과하면_OUT_OF_STOCK이다")
    void 수량_변경_시_판매_가능_수량을_초과하면_OUT_OF_STOCK이다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("재고상품", 3, (short) 5, adminId);
        CartOwner guest = CartOwner.ofGuest(UUID.randomUUID());
        CartService.AddItemResult added = cartService.addItem(guest, product.getId(), 1);

        assertThatThrownBy(() -> cartService.changeQuantity(guest, added.cartItemId(), 4))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("HIDDEN_상품은_담을_수_없고_PRODUCT_NOT_ON_SALE이다")
    void HIDDEN_상품은_담을_수_없고_PRODUCT_NOT_ON_SALE이다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("숨김예정상품", 10, (short) 5, adminId);
        productStatusService.changeStatus(product.getId(), ProductStatus.HIDDEN, adminId);
        CartOwner guest = CartOwner.ofGuest(UUID.randomUUID());

        assertThatThrownBy(() -> cartService.addItem(guest, product.getId(), 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_ON_SALE);
    }
}
