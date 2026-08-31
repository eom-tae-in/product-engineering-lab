package kr.savepick.cart.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 10-erd.md cart_items 테이블. 장바구니에 담긴 품목과 수량 (BR-009, BR-010).
 * {@code quantity <= products.max_order_quantity}, 장바구니당 품목 수 10개 이하 판정은
 * {@link CartLimitPolicy}가 맡고, 이 엔티티는 그 판정이 끝난 뒤에만 상태를 바꾼다.
 */
@Entity
@Table(name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private short quantity;

    @Column(name = "added_price", nullable = false)
    private int addedPrice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CartItem() {
    }

    private CartItem(Long cartId, Long productId, short quantity, int addedPrice, LocalDateTime now) {
        this.cartId = cartId;
        this.productId = productId;
        this.quantity = quantity;
        this.addedPrice = addedPrice;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** API-013 담기 — 새 품목 생성. 한도 판정(CartLimitPolicy)이 끝난 뒤에만 호출한다. */
    public static CartItem add(Long cartId, Long productId, short quantity, int addedPrice, LocalDateTime now) {
        return new CartItem(cartId, productId, quantity, addedPrice, now);
    }

    /**
     * API-013(재담기 합산)·API-014(수량 변경)·병합에서 공통으로 쓰는 수량 갱신.
     * {@code addedPrice}도 함께 갱신해 이 변경 시점의 표시가로 다시 맞춘다.
     */
    public void changeQuantity(short quantity, int addedPrice, LocalDateTime now) {
        this.quantity = quantity;
        this.addedPrice = addedPrice;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getCartId() {
        return cartId;
    }

    public Long getProductId() {
        return productId;
    }

    public short getQuantity() {
        return quantity;
    }

    public int getAddedPrice() {
        return addedPrice;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
