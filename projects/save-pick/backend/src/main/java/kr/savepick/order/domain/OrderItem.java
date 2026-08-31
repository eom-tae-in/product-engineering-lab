package kr.savepick.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 10-erd.md order_items 테이블. 주문 시점의 상품 정보·가격 스냅샷(BR-005) — 이후 상품이
 * 바뀌어도 이 값은 불변이다. 수정 메서드를 두지 않는다(스냅샷이므로).
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "sale_unit", nullable = false, length = 20)
    private String saleUnit;

    @Column(nullable = false)
    private short quantity;

    @Column(name = "original_unit_price", nullable = false)
    private int originalUnitPrice;

    @Column(name = "discount_rate", nullable = false)
    private short discountRate;

    @Column(name = "unit_price", nullable = false)
    private int unitPrice;

    @Column(name = "line_amount", nullable = false)
    private int lineAmount;

    @Column(name = "product_closing_at", nullable = false)
    private LocalDateTime productClosingAt;

    protected OrderItem() {
    }

    private OrderItem(
            Long orderId, Long productId, String productName, String saleUnit, short quantity,
            int originalUnitPrice, short discountRate, int unitPrice, int lineAmount, LocalDateTime productClosingAt) {
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.saleUnit = saleUnit;
        this.quantity = quantity;
        this.originalUnitPrice = originalUnitPrice;
        this.discountRate = discountRate;
        this.unitPrice = unitPrice;
        this.lineAmount = lineAmount;
        this.productClosingAt = productClosingAt;
    }

    /** BR-005 — 주문서 생성 시점의 할인가로 고정한다. */
    public static OrderItem snapshot(
            Long orderId, Long productId, String productName, String saleUnit, short quantity,
            int originalUnitPrice, short discountRate, int unitPrice, int lineAmount, LocalDateTime productClosingAt) {
        return new OrderItem(
                orderId, productId, productName, saleUnit, quantity, originalUnitPrice, discountRate,
                unitPrice, lineAmount, productClosingAt);
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

    public String getProductName() {
        return productName;
    }

    public String getSaleUnit() {
        return saleUnit;
    }

    public short getQuantity() {
        return quantity;
    }

    public int getOriginalUnitPrice() {
        return originalUnitPrice;
    }

    public short getDiscountRate() {
        return discountRate;
    }

    public int getUnitPrice() {
        return unitPrice;
    }

    public int getLineAmount() {
        return lineAmount;
    }

    public LocalDateTime getProductClosingAt() {
        return productClosingAt;
    }
}
