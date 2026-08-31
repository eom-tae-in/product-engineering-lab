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
import kr.savepick.common.audit.ActorType;

/**
 * 10-erd.md stock_ledgers 테이블. 재고 수량 변경의 추가 전용 원장 (13번 §1, §9.2 관측 지표).
 * 수정·삭제하지 않는다 — 이 엔티티에는 상태 변경 메서드가 없다.
 */
@Entity
@Table(name = "stock_ledgers")
public class StockLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockChangeReason reason;

    @Column(name = "delta_total", nullable = false)
    private int deltaTotal;

    @Column(name = "delta_held", nullable = false)
    private int deltaHeld;

    @Column(name = "delta_confirmed", nullable = false)
    private int deltaConfirmed;

    @Column(name = "delta_discarded", nullable = false)
    private int deltaDiscarded;

    @Column(name = "after_total", nullable = false)
    private int afterTotal;

    @Column(name = "after_available", nullable = false)
    private int afterAvailable;

    @Column(name = "after_held", nullable = false)
    private int afterHeld;

    @Column(name = "after_confirmed", nullable = false)
    private int afterConfirmed;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 10)
    private ActorType actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(length = 200)
    private String note;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected StockLedger() {
    }

    private StockLedger(
            Long productId, Long orderId, StockChangeReason reason,
            int deltaTotal, int deltaHeld, int deltaConfirmed, int deltaDiscarded,
            int afterTotal, int afterAvailable, int afterHeld, int afterConfirmed,
            ActorType actorType, Long actorId, String note, LocalDateTime occurredAt) {
        this.productId = productId;
        this.orderId = orderId;
        this.reason = reason;
        this.deltaTotal = deltaTotal;
        this.deltaHeld = deltaHeld;
        this.deltaConfirmed = deltaConfirmed;
        this.deltaDiscarded = deltaDiscarded;
        this.afterTotal = afterTotal;
        this.afterAvailable = afterAvailable;
        this.afterHeld = afterHeld;
        this.afterConfirmed = afterConfirmed;
        this.actorType = actorType;
        this.actorId = actorId;
        this.note = note;
        this.occurredAt = occurredAt;
    }

    public static StockLedger record(
            Long productId, Long orderId, StockChangeReason reason,
            int deltaTotal, int deltaHeld, int deltaConfirmed, int deltaDiscarded,
            int afterTotal, int afterAvailable, int afterHeld, int afterConfirmed,
            ActorType actorType, Long actorId, String note, LocalDateTime occurredAt) {
        return new StockLedger(
                productId, orderId, reason, deltaTotal, deltaHeld, deltaConfirmed, deltaDiscarded,
                afterTotal, afterAvailable, afterHeld, afterConfirmed, actorType, actorId, note, occurredAt);
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public StockChangeReason getReason() {
        return reason;
    }

    public int getDeltaTotal() {
        return deltaTotal;
    }

    public int getDeltaHeld() {
        return deltaHeld;
    }

    public int getDeltaConfirmed() {
        return deltaConfirmed;
    }

    public int getDeltaDiscarded() {
        return deltaDiscarded;
    }

    public int getAfterTotal() {
        return afterTotal;
    }

    public int getAfterAvailable() {
        return afterAvailable;
    }

    public int getAfterHeld() {
        return afterHeld;
    }

    public int getAfterConfirmed() {
        return afterConfirmed;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
