package kr.savepick.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 10-erd.md products 테이블. 상품 정보와 판매 상태의 정본 (05-state-rules.md §5).
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private short storeId;

    @Column(nullable = false, length = 100)
    private String name;

    /** TEXT 컬럼 — VARCHAR(255) 기본 추정과 스키마 검증이 어긋나지 않도록 명시적으로 타입을 지정한다. */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column
    private String description;

    @Column(name = "sale_unit", nullable = false, length = 20)
    private String saleUnit;

    @Column(name = "original_price", nullable = false)
    private int originalPrice;

    @Column(name = "closing_at", nullable = false)
    private LocalDateTime closingAt;

    @Column(name = "max_order_quantity", nullable = false)
    private short maxOrderQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProductStatus status;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Product() {
    }

    private Product(
            short storeId, String name, String description, String saleUnit, int originalPrice,
            LocalDateTime closingAt, short maxOrderQuantity, LocalDateTime now) {
        this.storeId = storeId;
        this.name = name;
        this.description = description;
        this.saleUnit = saleUnit;
        this.originalPrice = originalPrice;
        this.closingAt = closingAt;
        this.maxOrderQuantity = maxOrderQuantity;
        this.status = ProductStatus.DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** API-103 상품 등록. 등록 직후 상태는 항상 DRAFT다 (05 §5.2). */
    public static Product register(
            short storeId, String name, String description, String saleUnit, int originalPrice,
            LocalDateTime closingAt, short maxOrderQuantity, LocalDateTime now) {
        return new Product(storeId, name, description, saleUnit, originalPrice, closingAt, maxOrderQuantity, now);
    }

    /** API-105 상품 수정. 개별 필드 값은 application 계층이 null 여부로 부분 수정을 판단한다. */
    public void updateName(String name, LocalDateTime now) {
        this.name = name;
        this.updatedAt = now;
    }

    public void updateDescription(String description, LocalDateTime now) {
        this.description = description;
        this.updatedAt = now;
    }

    public void updateSaleUnit(String saleUnit, LocalDateTime now) {
        this.saleUnit = saleUnit;
        this.updatedAt = now;
    }

    public void updateOriginalPrice(int originalPrice, LocalDateTime now) {
        this.originalPrice = originalPrice;
        this.updatedAt = now;
    }

    public void updateClosingAt(LocalDateTime closingAt, LocalDateTime now) {
        this.closingAt = closingAt;
        this.updatedAt = now;
    }

    public void updateMaxOrderQuantity(short maxOrderQuantity, LocalDateTime now) {
        this.maxOrderQuantity = maxOrderQuantity;
        this.updatedAt = now;
    }

    /**
     * 05-state-rules.md §5.2·5.3 — 관리자가 조작하는 상태 전이(DRAFT→ON_SALE, ON_SALE↔HIDDEN)만
     * 판정한다. CLOSED로의 전이는 시각 도달로만 일어나며 이 메서드로 요청할 수 없다 (API-106).
     */
    public boolean canTransitionTo(ProductStatus target, boolean stockRegistered) {
        if (this.status == ProductStatus.CLOSED) {
            return false;
        }
        if (target == ProductStatus.CLOSED) {
            return false;
        }
        return switch (this.status) {
            case DRAFT -> target == ProductStatus.ON_SALE && stockRegistered;
            case ON_SALE -> target == ProductStatus.HIDDEN;
            case HIDDEN -> target == ProductStatus.ON_SALE;
            case CLOSED -> false;
        };
    }

    /** DRAFT → ON_SALE(판매 시작) 또는 HIDDEN → ON_SALE(판매 재개). canTransitionTo가 true인 뒤에만 호출한다. */
    public void startSale(LocalDateTime now) {
        this.status = ProductStatus.ON_SALE;
        this.updatedAt = now;
    }

    /** ON_SALE → HIDDEN(판매 일시 중지). canTransitionTo가 true인 뒤에만 호출한다. */
    public void hide(LocalDateTime now) {
        this.status = ProductStatus.HIDDEN;
        this.updatedAt = now;
    }

    /**
     * BR-030 — 마감 시각 도달에 따른 시스템 전이. 관리자 전이와 달리 어떤 상태에서도(CLOSED 제외)
     * 허용된다. CLOSED 상품의 마감 시각은 이후 수정할 수 없다 (05 §5.3).
     */
    public boolean closeIfDue(LocalDateTime now) {
        if (this.status == ProductStatus.CLOSED) {
            return false;
        }
        if (DiscountRatePolicy.isClosed(this.closingAt, now)) {
            this.status = ProductStatus.CLOSED;
            this.closedAt = now;
            this.updatedAt = now;
            return true;
        }
        return false;
    }

    public boolean isClosingAtEditable() {
        return this.status != ProductStatus.CLOSED;
    }

    public Long getId() {
        return id;
    }

    public short getStoreId() {
        return storeId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getSaleUnit() {
        return saleUnit;
    }

    public int getOriginalPrice() {
        return originalPrice;
    }

    public LocalDateTime getClosingAt() {
        return closingAt;
    }

    public short getMaxOrderQuantity() {
        return maxOrderQuantity;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
