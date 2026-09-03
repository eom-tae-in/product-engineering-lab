package kr.savepick.cart.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.savepick.cart.domain.Cart;
import kr.savepick.cart.domain.CartRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartJpaRepository extends JpaRepository<Cart, Long>, CartRepository {

    @Override
    Optional<Cart> findByMemberId(Long memberId);

    @Override
    Optional<Cart> findByGuestToken(UUID guestToken);

    /** BATCH-06 대상 조회 — 한 번에 한 덩어리씩만 가져와 삭제 트랜잭션을 짧게 유지한다. */
    @Query("select c.id from Cart c where c.guestToken is not null and c.updatedAt < :threshold order by c.updatedAt asc")
    List<Long> findGuestCartIdsUpdatedBefore(@Param("threshold") LocalDateTime threshold, Pageable limit);

    @Override
    default List<Long> findGuestCartIdsUpdatedBefore(LocalDateTime threshold, int limit) {
        return findGuestCartIdsUpdatedBefore(threshold, PageRequest.of(0, limit));
    }

    /**
     * {@code cart_items.cart_id}가 {@code ON DELETE CASCADE}라 DB가 품목까지 함께 지운다
     * (10-erd.md §4 carts). JPQL 벌크 삭제는 영속성 컨텍스트를 거치지 않으므로
     * {@code clearAutomatically}로 남은 1차 캐시를 비운다.
     */
    @Override
    @Modifying(clearAutomatically = true)
    @Query("delete from Cart c where c.id in :ids")
    int deleteByIdIn(@Param("ids") List<Long> ids);
}
