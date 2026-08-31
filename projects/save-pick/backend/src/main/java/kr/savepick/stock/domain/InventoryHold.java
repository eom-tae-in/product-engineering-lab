package kr.savepick.stock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 10-erd.md inventory_holds 테이블. 주문 1건 × 품목 1개당 1행 (13번 §1).
 * 상태 전이는 이 엔티티의 세터가 아니라 항상 조건부 UPDATE(WHERE status = 'HELD')로 실행한다
 * (14-project-structure.md §5 강제 규칙 2) — 실제 쓰기는
 * {@code InventoryHoldRepository#transitionStatus}를 통해서만 한다.
 */
@Entity
@Table(name = "inventory_holds")
public class InventoryHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private short quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private HoldStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected InventoryHold() {
    }

    private InventoryHold(Long orderId, Long productId, short quantity, LocalDateTime expiresAt, LocalDateTime now) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = HoldStatus.HELD;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    /** BR-007, BR-027 — 주문서 생성 시점에 HELD로 만든다. 연장하지 않는다. */
    public static InventoryHold create(Long orderId, Long productId, short quantity, LocalDateTime expiresAt, LocalDateTime now) {
        return new InventoryHold(orderId, productId, quantity, expiresAt, now);
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getProductId() {
        return productId;
    }

    public short getQuantity() {
        return quantity;
    }

    public HoldStatus getStatus() {
        return status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getSettledAt() {
        return settledAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
