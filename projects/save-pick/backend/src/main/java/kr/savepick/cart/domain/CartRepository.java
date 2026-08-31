package kr.savepick.cart.domain;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository {

    Cart save(Cart cart);

    Optional<Cart> findById(Long id);

    Optional<Cart> findByMemberId(Long memberId);

    Optional<Cart> findByGuestToken(UUID guestToken);

    void delete(Cart cart);
}
