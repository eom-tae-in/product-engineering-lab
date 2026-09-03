package kr.savepick.cart.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository {

    Cart save(Cart cart);

    Optional<Cart> findById(Long id);

    Optional<Cart> findByMemberId(Long memberId);

    Optional<Cart> findByGuestToken(UUID guestToken);

    void delete(Cart cart);

    /**
     * BATCH-06 — 보관 기간이 지난 게스트 장바구니 (10-erd.md §8:
     * {@code updated_at < now() - 7일}인 {@code guest_token} 장바구니). 회원 장바구니는
     * 대상이 아니다.
     */
    List<Long> findGuestCartIdsUpdatedBefore(LocalDateTime threshold, int limit);

    /** @return 실제로 삭제한 행 수. {@code cart_items}는 FK의 ON DELETE CASCADE로 함께 지워진다. */
    int deleteByIdIn(List<Long> ids);
}
