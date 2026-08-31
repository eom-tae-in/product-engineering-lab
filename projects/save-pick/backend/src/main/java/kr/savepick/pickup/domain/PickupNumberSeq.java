package kr.savepick.pickup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 10-erd.md pickup_number_seqs 테이블. 영업일별 마지막 발급 픽업 번호(BR-026).
 * 발급(원자적 증가)은 {@code pickup/infrastructure/PickupNumberSeqJpaRepository}가 맡는다 —
 * 이 엔티티의 세터로 값을 바꾸지 않는다.
 */
@Entity
@Table(name = "pickup_number_seqs")
public class PickupNumberSeq {

    @Id
    @Column(name = "business_date")
    private LocalDate businessDate;

    @Column(name = "last_number", nullable = false)
    private short lastNumber;

    protected PickupNumberSeq() {
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public short getLastNumber() {
        return lastNumber;
    }
}
