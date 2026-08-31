package kr.savepick.store.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 10-erd.md store_holidays 테이블. 매장 휴무일 지정.
 */
@Entity
@Table(name = "store_holidays")
public class StoreHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private short storeId;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(length = 100)
    private String memo;

    protected StoreHoliday() {
    }

    private StoreHoliday(short storeId, LocalDate holidayDate, String memo) {
        this.storeId = storeId;
        this.holidayDate = holidayDate;
        this.memo = memo;
    }

    /** API-121 휴무일 전체 교체 시 새로 생성하는 팩토리. */
    public static StoreHoliday of(short storeId, LocalDate holidayDate) {
        return new StoreHoliday(storeId, holidayDate, null);
    }

    public Long getId() {
        return id;
    }

    public short getStoreId() {
        return storeId;
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public String getMemo() {
        return memo;
    }
}
