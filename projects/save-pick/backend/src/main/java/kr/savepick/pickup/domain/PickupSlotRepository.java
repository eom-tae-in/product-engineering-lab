package kr.savepick.pickup.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PickupSlotRepository {

    PickupSlot save(PickupSlot slot);

    Optional<PickupSlot> findById(Long id);

    /** 13번 §7.1, §8 — 락 순서 4번. 정원 점유·반납 트랜잭션에서만 쓴다. */
    Optional<PickupSlot> findByIdForUpdate(Long id);

    List<PickupSlot> findByStoreIdAndSlotDateOrderByStartAtAsc(short storeId, LocalDate slotDate);

    /**
     * BATCH-05 — {@code UNIQUE (store_id, start_at)}를 이용한 멱등 삽입
     * ({@code INSERT ... ON CONFLICT (store_id, start_at) DO NOTHING}). 이미 있으면 0을 돌려준다.
     */
    int insertIfAbsent(short storeId, LocalDate slotDate, LocalDateTime startAt, LocalDateTime endAt, short capacity, LocalDateTime now);

    /** {@code findByIdForUpdate}로 이미 잠근 뒤에만 호출한다 (13번 §8). */
    int incrementReservedCount(Long id);

    /** {@code GREATEST(reserved_count - 1, 0)} — 이미 잠근 뒤에만 호출한다 (13번 §8). */
    int decrementReservedCount(Long id);
}
