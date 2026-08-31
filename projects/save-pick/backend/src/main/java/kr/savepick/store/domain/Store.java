package kr.savepick.store.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 10-erd.md stores 테이블. 단일 매장 정보와 운영 설정 (BR-001).
 * id는 항상 1이다 — DB의 CHK_stores_single_row 제약과 짝을 이룬다.
 */
@Entity
@Table(name = "stores")
public class Store {

    /** BR-001 단일 매장. stores 테이블은 이 값 하나만 가진다. */
    public static final short SINGLETON_ID = 1;

    @Id
    private short id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    @Column(name = "slot_unit_minutes", nullable = false)
    private short slotUnitMinutes;

    @Column(name = "default_slot_capacity", nullable = false)
    private short defaultSlotCapacity;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Store() {
    }

    /**
     * API-121 매장 운영 설정 변경 (BR-014 영업시간, BR-016 픽업 정원).
     * name·address·phone·slotUnitMinutes는 이 API로 바꾸지 않는다 (11-api-spec.md API-121).
     */
    public void updateOperatingSettings(BusinessHours businessHours, short defaultSlotCapacity, LocalDateTime now) {
        this.openTime = businessHours.openTime();
        this.closeTime = businessHours.closeTime();
        this.defaultSlotCapacity = defaultSlotCapacity;
        this.updatedAt = now;
    }

    public short getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }

    public short getSlotUnitMinutes() {
        return slotUnitMinutes;
    }

    public short getDefaultSlotCapacity() {
        return defaultSlotCapacity;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
