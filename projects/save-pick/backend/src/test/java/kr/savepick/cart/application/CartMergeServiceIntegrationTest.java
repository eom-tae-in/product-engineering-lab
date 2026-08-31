package kr.savepick.cart.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.cart.domain.Cart;
import kr.savepick.cart.domain.CartItem;
import kr.savepick.cart.domain.CartItemRepository;
import kr.savepick.cart.domain.CartRepository;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.application.ProductStatusService;
import kr.savepick.product.domain.Product;
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
 * 10-erd.md §5.1, 02 CS-01 7단계 — 게스트 장바구니를 회원 장바구니로 병합한다.
 * account 슬라이스에서 CartMergeService를 호출하는 통합 흐름은
 * account/api/AuthCartMergeApiIntegrationTest에서 HTTP 경로로 검증한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class CartMergeServiceIntegrationTest {

    @Autowired
    private CartMergeService cartMergeService;

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

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

    private Long registerMember() {
        Member member = Member.registerCustomer(
                "member-" + UUID.randomUUID() + "@test.com", "hash", "회원", "01011112222", serverClock.now());
        return memberRepository.save(member).getId();
    }

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
    @DisplayName("게스트_토큰이_없으면_병합하지_않는다")
    void 게스트_토큰이_없으면_병합하지_않는다() {
        Long memberId = registerMember();
        boolean merged = cartMergeService.mergeGuestCartIntoMember(memberId, null);
        assertThat(merged).isFalse();
    }

    @Test
    @DisplayName("게스트_장바구니가_없으면_병합하지_않는다")
    void 게스트_장바구니가_없으면_병합하지_않는다() {
        Long memberId = registerMember();
        boolean merged = cartMergeService.mergeGuestCartIntoMember(memberId, UUID.randomUUID());
        assertThat(merged).isFalse();
    }

    @Test
    @DisplayName("게스트_장바구니를_병합하면_회원_장바구니로_옮겨지고_게스트_장바구니는_삭제된다")
    void 게스트_장바구니를_병합하면_회원_장바구니로_옮겨지고_게스트_장바구니는_삭제된다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("병합상품", 20, (short) 5, adminId);
        UUID guestToken = UUID.randomUUID();
        cartService.addItem(CartOwner.ofGuest(guestToken), product.getId(), 2);
        Long memberId = registerMember();

        boolean merged = cartMergeService.mergeGuestCartIntoMember(memberId, guestToken);

        assertThat(merged).isTrue();
        assertThat(cartRepository.findByGuestToken(guestToken)).isEmpty();
        Cart memberCart = cartRepository.findByMemberId(memberId).orElseThrow();
        var items = cartItemRepository.findByCartId(memberCart.getId());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getQuantity()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("같은_상품이_이미_회원_장바구니에_있으면_수량을_합산한다")
    void 같은_상품이_이미_회원_장바구니에_있으면_수량을_합산한다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("합산병합상품", 20, (short) 5, adminId);
        Long memberId = registerMember();
        cartService.addItem(CartOwner.ofMember(memberId), product.getId(), 2);

        UUID guestToken = UUID.randomUUID();
        cartService.addItem(CartOwner.ofGuest(guestToken), product.getId(), 1);

        boolean merged = cartMergeService.mergeGuestCartIntoMember(memberId, guestToken);

        assertThat(merged).isTrue();
        Cart memberCart = cartRepository.findByMemberId(memberId).orElseThrow();
        var items = cartItemRepository.findByCartId(memberCart.getId());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getQuantity()).isEqualTo((short) 3);
    }

    @Test
    @DisplayName("합산_수량이_최대_주문_수량을_넘으면_절단한다")
    void 합산_수량이_최대_주문_수량을_넘으면_절단한다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("절단상품", 20, (short) 5, adminId);
        Long memberId = registerMember();
        cartService.addItem(CartOwner.ofMember(memberId), product.getId(), 4);

        UUID guestToken = UUID.randomUUID();
        cartService.addItem(CartOwner.ofGuest(guestToken), product.getId(), 4);

        cartMergeService.mergeGuestCartIntoMember(memberId, guestToken);

        Cart memberCart = cartRepository.findByMemberId(memberId).orElseThrow();
        CartItem item = cartItemRepository.findByCartId(memberCart.getId()).get(0);
        assertThat(item.getQuantity()).isEqualTo((short) 5);
    }
}
