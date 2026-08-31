package kr.savepick.cart.infrastructure;

import java.util.Optional;
import java.util.UUID;
import kr.savepick.cart.domain.Cart;
import kr.savepick.cart.domain.CartRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartJpaRepository extends JpaRepository<Cart, Long>, CartRepository {

    @Override
    Optional<Cart> findByMemberId(Long memberId);

    @Override
    Optional<Cart> findByGuestToken(UUID guestToken);
}
