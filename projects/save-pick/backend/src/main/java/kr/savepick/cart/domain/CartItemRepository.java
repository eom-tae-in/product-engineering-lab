package kr.savepick.cart.domain;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository {

    CartItem save(CartItem cartItem);

    Optional<CartItem> findById(Long id);

    List<CartItem> findByCartId(Long cartId);

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    long countByCartId(Long cartId);

    void delete(CartItem cartItem);
}
