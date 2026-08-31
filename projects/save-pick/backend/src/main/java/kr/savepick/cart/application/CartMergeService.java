package kr.savepick.cart.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.savepick.cart.domain.Cart;
import kr.savepick.cart.domain.CartItem;
import kr.savepick.cart.domain.CartItemRepository;
import kr.savepick.cart.domain.CartRepository;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 10-erd.md §5.1, 02 CS-01 7단계 — 회원가입·로그인 성공 뒤 게스트 장바구니를 회원 장바구니로
 * 병합하고 게스트 장바구니는 삭제한다. 같은 상품은 수량을 합산하되
 * {@code products.max_order_quantity}를 넘지 않게 절단한다(ERD 명시 규칙).
 *
 * <p>품목 10개 한도(BR-009)는 병합 시점에는 강제하지 않는다 — 문서에 명시가 없어 이 슬라이스에서
 * 임의로 정한 규칙이다: 병합은 사용자가 게스트 상태에서 담아둔 행동을 잃지 않는 것이 우선이고,
 * 한도 초과 여부는 다음 장바구니 조회(API-012)에서 {@code orderable: false}로 자연스럽게
 * 드러난다. 가격({@code added_price})도 병합 시점에 다시 계산하지 않는다 — 표시가는 어차피
 * 조회할 때마다 {@link CartValidationService}가 다시 계산하므로, 병합은 수량 이동에만 집중한다.
 */
@Service
public class CartMergeService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ServerClock serverClock;

    public CartMergeService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            ServerClock serverClock) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.serverClock = serverClock;
    }

    /**
     * @return 실제로 게스트 품목이 회원 장바구니로 옮겨졌으면 true. 게스트 토큰이 없거나,
     *         그 토큰의 장바구니가 없거나, 있어도 비어 있으면 false.
     */
    @Transactional
    public boolean mergeGuestCartIntoMember(Long memberId, UUID guestToken) {
        if (guestToken == null) {
            return false;
        }
        Optional<Cart> guestCartOpt = cartRepository.findByGuestToken(guestToken);
        if (guestCartOpt.isEmpty()) {
            return false;
        }

        Cart guestCart = guestCartOpt.get();
        List<CartItem> guestItems = cartItemRepository.findByCartId(guestCart.getId());
        if (guestItems.isEmpty()) {
            cartRepository.delete(guestCart);
            return false;
        }

        LocalDateTime now = serverClock.now();
        Cart memberCart = cartRepository.findByMemberId(memberId)
                .orElseGet(() -> cartRepository.save(Cart.forMember(memberId, now)));

        for (CartItem guestItem : guestItems) {
            mergeOneItem(memberCart, guestItem, now);
        }

        cartRepository.delete(guestCart);
        memberCart.touch(now);
        cartRepository.save(memberCart);
        return true;
    }

    private void mergeOneItem(Cart memberCart, CartItem guestItem, LocalDateTime now) {
        Optional<CartItem> existing = cartItemRepository.findByCartIdAndProductId(memberCart.getId(), guestItem.getProductId());
        int summed = guestItem.getQuantity() + existing.map(item -> (int) item.getQuantity()).orElse(0);
        short cap = productRepository.findById(guestItem.getProductId())
                .map(Product::getMaxOrderQuantity)
                .orElse((short) summed);
        short mergedQuantity = (short) Math.min(summed, cap);

        if (existing.isPresent()) {
            CartItem memberItem = existing.get();
            memberItem.changeQuantity(mergedQuantity, memberItem.getAddedPrice(), now);
            cartItemRepository.save(memberItem);
        } else {
            cartItemRepository.save(CartItem.add(memberCart.getId(), guestItem.getProductId(), mergedQuantity, guestItem.getAddedPrice(), now));
        }
    }
}
