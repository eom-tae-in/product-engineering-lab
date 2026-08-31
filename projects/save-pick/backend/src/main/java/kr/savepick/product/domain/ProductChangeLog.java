package kr.savepick.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 10-erd.md product_change_logs 테이블. API-105·API-106의 변경 이력 (FR-041).
 * changedField는 DB CHECK 제약과 동일한 스네이크 케이스 문자열
 * (name/description/sale_unit/original_price/closing_at/max_order_quantity/status)만 쓴다.
 */
@Entity
@Table(name = "product_change_logs")
public class ProductChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "changed_field", nullable = false, length = 30)
    private String changedField;

    @Column(name = "before_value", length = 255)
    private String beforeValue;

    @Column(name = "after_value", length = 255)
    private String afterValue;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    protected ProductChangeLog() {
    }

    private ProductChangeLog(
            Long productId, String changedField, String beforeValue, String afterValue,
            Long actorId, LocalDateTime changedAt) {
        this.productId = productId;
        this.changedField = changedField;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.actorId = actorId;
        this.changedAt = changedAt;
    }

    public static ProductChangeLog of(
            Long productId, String changedField, String beforeValue, String afterValue,
            Long actorId, LocalDateTime changedAt) {
        return new ProductChangeLog(productId, changedField, beforeValue, afterValue, actorId, changedAt);
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getChangedField() {
        return changedField;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public Long getActorId() {
        return actorId;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
