package kr.savepick.order.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.savepick.order.domain.Order;
import kr.savepick.order.domain.OrderRepository;
import kr.savepick.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 13번 §7.1 — 락 순서 1번({@code orders}). {@code findByIdAndMemberIdForUpdate}·
 * {@code findByIdForUpdate}가 결제·취소·포기·배치 흐름의 1층 방어선이다. 상태 전이는 모두
 * 조건부 UPDATE로 실행한다(14-project-structure.md §9.2 — JPA 더티 체킹이 아니라 명시적 쿼리).
 */
public interface OrderJpaRepository extends JpaRepository<Order, Long>, OrderRepository {

    @Override
    Optional<Order> findByIdAndMemberId(Long id, Long memberId);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id and o.memberId = :memberId")
    Optional<Order> findByIdAndMemberIdForUpdate(@Param("id") Long id, @Param("memberId") Long memberId);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Override
    boolean existsByMemberIdAndStatus(Long memberId, OrderStatus status);

    @Override
    Optional<Order> findByMemberIdAndStatus(Long memberId, OrderStatus status);

    @Override
    Optional<Order> findByPickupBusinessDateAndPickupNumber(LocalDate pickupBusinessDate, short pickupNumber);

    /** 14-project-structure.md §9.2 — 조건부 UPDATE. 영향 행 0이면 이미 PENDING이 아니었다는 뜻이다. */
    @Override
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = kr.savepick.order.domain.OrderStatus.EXPIRED, o.expiredAt = :now, o.updatedAt = :now "
            + "where o.id = :id and o.status = kr.savepick.order.domain.OrderStatus.PENDING")
    int expireIfPending(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Query("select o.id from Order o where o.status = kr.savepick.order.domain.OrderStatus.PENDING and o.holdExpiresAt <= :now order by o.holdExpiresAt asc")
    List<Long> findPendingIdsForHoldExpiry(@Param("now") LocalDateTime now, Pageable limit);

    @Override
    default List<Long> findPendingIdsForHoldExpiry(LocalDateTime now, int limit) {
        return findPendingIdsForHoldExpiry(now, PageRequest.of(0, limit));
    }

    @Query("select o.id from Order o where o.status in (kr.savepick.order.domain.OrderStatus.CONFIRMED, kr.savepick.order.domain.OrderStatus.READY) "
            + "and o.noShowDueAt <= :now order by o.noShowDueAt asc")
    List<Long> findConfirmedOrReadyIdsForNoShow(@Param("now") LocalDateTime now, Pageable limit);

    @Override
    default List<Long> findConfirmedOrReadyIdsForNoShow(LocalDateTime now, int limit) {
        return findConfirmedOrReadyIdsForNoShow(now, PageRequest.of(0, limit));
    }

    @Override
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.paymentAttemptCount = o.paymentAttemptCount + 1, o.updatedAt = :now where o.id = :id")
    int incrementPaymentAttemptCount(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Override
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = kr.savepick.order.domain.OrderStatus.CONFIRMED, "
            + "o.pickupNumber = :pickupNumber, o.cancelableUntil = :cancelableUntil, o.noShowDueAt = :noShowDueAt, "
            + "o.confirmedAt = :confirmedAt, o.updatedAt = :now "
            + "where o.id = :id and o.status = kr.savepick.order.domain.OrderStatus.PENDING")
    int confirmPending(
            @Param("id") Long id, @Param("pickupNumber") short pickupNumber, @Param("cancelableUntil") LocalDateTime cancelableUntil,
            @Param("noShowDueAt") LocalDateTime noShowDueAt, @Param("confirmedAt") LocalDateTime confirmedAt, @Param("now") LocalDateTime now);

    @Override
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = kr.savepick.order.domain.OrderStatus.FAILED, "
            + "o.failedAt = :now, o.stockSettledAt = :now, o.updatedAt = :now "
            + "where o.id = :id and o.status = kr.savepick.order.domain.OrderStatus.PENDING")
    int failPending(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Override
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = kr.savepick.order.domain.OrderStatus.READY, o.readyAt = :now, o.updatedAt = :now "
            + "where o.id = :id and o.status = kr.savepick.order.domain.OrderStatus.CONFIRMED")
    int transitionToReady(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Override
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = kr.savepick.order.domain.OrderStatus.COMPLETED, o.completedAt = :now, o.updatedAt = :now "
            + "where o.id = :id and o.status in (kr.savepick.order.domain.OrderStatus.CONFIRMED, kr.savepick.order.domain.OrderStatus.READY)")
    int transitionToCompleted(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Override
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = kr.savepick.order.domain.OrderStatus.CANCELED, "
            + "o.canceledBy = :canceledBy, o.cancelReason = :cancelReason, o.canceledAt = :now, o.updatedAt = :now "
            + "where o.id = :id and o.status in (kr.savepick.order.domain.OrderStatus.CONFIRMED, kr.savepick.order.domain.OrderStatus.READY)")
    int transitionToCanceled(
            @Param("id") Long id, @Param("canceledBy") String canceledBy, @Param("cancelReason") String cancelReason, @Param("now") LocalDateTime now);

    @Override
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.stockSettledAt = :now, o.updatedAt = :now where o.id = :id")
    int markStockSettled(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Override
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = kr.savepick.order.domain.OrderStatus.NO_SHOW, o.noShowAt = :now, o.updatedAt = :now "
            + "where o.id = :id and o.status in (kr.savepick.order.domain.OrderStatus.CONFIRMED, kr.savepick.order.domain.OrderStatus.READY)")
    int transitionToNoShow(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Query("select o from Order o where o.memberId = :memberId and o.status in :statuses order by o.createdAt desc")
    Page<Order> findByMemberIdAndStatusInPageable(
            @Param("memberId") Long memberId, @Param("statuses") List<OrderStatus> statuses, Pageable pageable);

    @Override
    default OrderPage findByMemberIdAndStatusIn(Long memberId, List<OrderStatus> statuses, int page, int size) {
        Page<Order> result = findByMemberIdAndStatusInPageable(memberId, statuses, PageRequest.of(page, size));
        return new OrderPage(result.getContent(), result.getTotalElements());
    }

    @Query("select o from Order o where o.pickupBusinessDate in :dates and o.status in :statuses "
            + "and (:slotId is null or o.pickupSlotId = :slotId) "
            + "order by o.pickupBusinessDate asc, o.pickupSlotId asc, o.createdAt asc")
    Page<Order> findForAdminPageable(
            @Param("dates") List<LocalDate> dates, @Param("statuses") List<OrderStatus> statuses,
            @Param("slotId") Long slotId, Pageable pageable);

    @Override
    default OrderPage findForAdmin(List<LocalDate> pickupDates, List<OrderStatus> statuses, Long slotId, int page, int size) {
        Page<Order> result = findForAdminPageable(pickupDates, statuses, slotId, PageRequest.of(page, size));
        return new OrderPage(result.getContent(), result.getTotalElements());
    }
}
