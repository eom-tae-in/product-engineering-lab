package kr.savepick.order.domain;

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
 * 10-erd.md order_status_histories 테이블. 추가 전용 이력(05번 상태 이력) — 수정 메서드를 두지
 * 않는다. {@code UNIQUE (order_id, to_status)}가 같은 상태로의 중복 기록을 막는다.
 */
@Entity
@Table(name = "order_status_histories")
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 12)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 12)
    private OrderStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 10)
    private ActorType actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(length = 200)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected OrderStatusHistory() {
    }

    private OrderStatusHistory(
            Long orderId, OrderStatus fromStatus, OrderStatus toStatus, ActorType actorType, Long actorId,
            String reason, LocalDateTime occurredAt) {
        this.orderId = orderId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorType = actorType;
        this.actorId = actorId;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public static OrderStatusHistory record(
            Long orderId, OrderStatus fromStatus, OrderStatus toStatus, ActorType actorType, Long actorId,
            String reason, LocalDateTime occurredAt) {
        return new OrderStatusHistory(orderId, fromStatus, toStatus, actorType, actorId, reason, occurredAt);
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
