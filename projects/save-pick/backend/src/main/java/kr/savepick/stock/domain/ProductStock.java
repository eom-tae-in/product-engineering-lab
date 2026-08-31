package kr.savepick.stock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 10-erd.md product_stocks 테이블. 상품당 1행. 수량 변경은 이 엔티티의 세터가 아니라
 * {@code stock/application/StockLedgerRecorder}가 발행하는 명시적 UPDATE로만 이뤄진다
 * (14-project-structure.md §5 강제 규칙 1, §9.2 — 더티 체킹으로 수량을 바꾸지 않는다).
 * 이 엔티티는 {@code FOR UPDATE} 락 조회와 읽기 전용 조회에만 쓴다.
 */
@Entity
@Table(name = "product_stocks")
public class ProductStock {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "held_quantity", nullable = false)
    private int heldQuantity;

    @Column(name = "confirmed_quantity", nullable = false)
    private int confirmedQuantity;

    @Column(name = "discarded_quantity", nullable = false)
    private int discardedQuantity;

    /** 생성 컬럼(GENERATED ALWAYS AS). 애플리케이션에서 쓰지 않고 읽기만 한다. */
    @Column(name = "available_quantity", insertable = false, updatable = false)
    private int availableQuantity;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ProductStock() {
    }

    public Long getProductId() {
        return productId;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public int getHeldQuantity() {
        return heldQuantity;
    }

    public int getConfirmedQuantity() {
        return confirmedQuantity;
    }

    public int getDiscardedQuantity() {
        return discardedQuantity;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
