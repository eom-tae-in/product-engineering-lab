package kr.savepick.pickup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 10-erd.md pickup_slots 테이블. 매장 하루 영업시간을 30분 단위로 나눈 시간대 1개(BR-014).
 * {@code reservedCount}는 결제 성공 시점에만 늘어난다(05 §8, A10) — 이 엔티티의 세터가 아니라
 * {@code pickup/infrastructure/PickupSlotJpaRepository}의 조건부 UPDATE를 통해서만 바뀐다
 * (14-project-structure.md §9.2, stock의 ProductStock과 같은 패턴).
 */
@Entity
@Table(name = "pickup_slots")
public class PickupSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private short storeId;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false)
    private short capacity;

    @Column(name = "reserved_count", nullable = false)
    private short reservedCount;

    @Column(nullable = false)
    private boolean blocked;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PickupSlot() {
    }

    private PickupSlot(short storeId, LocalDate slotDate, LocalDateTime startAt, LocalDateTime endAt, short capacity, LocalDateTime now) {
        this.storeId = storeId;
        this.slotDate = slotDate;
        this.startAt = startAt;
        this.endAt = endAt;
        this.capacity = capacity;
        this.reservedCount = 0;
        this.blocked = false;
        this.createdAt = now;
    }

    /** BATCH-05 — 생성 시점 {@code stores.default_slot_capacity}를 복사한다 (10번 §3.3). */
    public static PickupSlot create(short storeId, LocalDate slotDate, LocalDateTime startAt, LocalDateTime endAt, short capacity, LocalDateTime now) {
        return new PickupSlot(storeId, slotDate, startAt, endAt, capacity, now);
    }

    /** API-119 — 정원 변경. 기존 예약이 새 정원을 넘어도 그대로 둔다(BR-016). */
    public void updateCapacity(short capacity) {
        this.capacity = capacity;
    }

    /** API-119 — 관리자 개별 차단(FR-058). */
    public void block() {
        this.blocked = true;
    }

    public void unblock() {
        this.blocked = false;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public boolean isFull() {
        return reservedCount >= capacity;
    }

    public Long getId() {
        return id;
    }

    public short getStoreId() {
        return storeId;
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public short getCapacity() {
        return capacity;
    }

    public short getReservedCount() {
        return reservedCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
