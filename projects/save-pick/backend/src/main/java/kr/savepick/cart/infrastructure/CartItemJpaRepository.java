package kr.savepick.cart.infrastructure;

import java.util.List;
import java.util.Optional;
import kr.savepick.cart.domain.CartItem;
import kr.savepick.cart.domain.CartItemRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemJpaRepository extends JpaRepository<CartItem, Long>, CartItemRepository {

    @Override
    List<CartItem> findByCartId(Long cartId);

    @Override
    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    @Override
    long countByCartId(Long cartId);
}
