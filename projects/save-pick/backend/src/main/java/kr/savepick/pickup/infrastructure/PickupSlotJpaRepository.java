package kr.savepick.pickup.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.savepick.pickup.domain.PickupSlot;
import kr.savepick.pickup.domain.PickupSlotRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 13번 §7.1, §8 — 락 순서 4번({@code product_stocks} 다음). {@code findByIdForUpdate}가 정원
 * 점유·반납의 1층 방어선이다.
 */
public interface PickupSlotJpaRepository extends JpaRepository<PickupSlot, Long>, PickupSlotRepository {

    @Override
    List<PickupSlot> findByStoreIdAndSlotDateOrderByStartAtAsc(short storeId, LocalDate slotDate);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PickupSlot s where s.id = :id")
    Optional<PickupSlot> findByIdForUpdate(@Param("id") Long id);

    /** BATCH-05 — {@code UNIQUE(store_id, start_at)} 덕분에 중복 실행해도 안전하다(멱등, TC-104). */
    @Override
    @Modifying(clearAutomatically = true)
    @Query(value = "INSERT INTO pickup_slots (store_id, slot_date, start_at, end_at, capacity, reserved_count, blocked, created_at) "
            + "VALUES (:storeId, :slotDate, :startAt, :endAt, :capacity, 0, false, :now) "
            + "ON CONFLICT (store_id, start_at) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(
            @Param("storeId") short storeId,
            @Param("slotDate") LocalDate slotDate,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("capacity") short capacity,
            @Param("now") LocalDateTime now);

    /** {@code findByIdForUpdate}로 이미 잠근 뒤에만 호출한다(13번 §8). */
    @Override
    @Modifying(clearAutomatically = true)
    @Query("update PickupSlot p set p.reservedCount = p.reservedCount + 1 where p.id = :id")
    int incrementReservedCount(@Param("id") Long id);

    /** {@code GREATEST(reserved_count - 1, 0)} — 이미 잠근 뒤에만 호출한다(13번 §8). */
    @Override
    @Modifying(clearAutomatically = true)
    @Query(value = "update pickup_slots set reserved_count = GREATEST(reserved_count - 1, 0) where id = :id", nativeQuery = true)
    int decrementReservedCount(@Param("id") Long id);
}
