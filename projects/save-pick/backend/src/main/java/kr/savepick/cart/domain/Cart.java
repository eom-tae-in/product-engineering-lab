package kr.savepick.cart.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 10-erd.md carts 테이블. 회원 또는 게스트 토큰 단위 장바구니의 정본 (BR-010 — 재고를 건드리지 않는다).
 * {@code memberId}와 {@code guestToken} 중 하나는 항상 채워져 있다 (CHK_carts_owner_present).
 */
@Entity
@Table(name = "carts")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "guest_token")
    private UUID guestToken;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Cart() {
    }

    private Cart(Long memberId, UUID guestToken, LocalDateTime now) {
        this.memberId = memberId;
        this.guestToken = guestToken;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Cart forMember(Long memberId, LocalDateTime now) {
        return new Cart(memberId, null, now);
    }

    public static Cart forGuest(UUID guestToken, LocalDateTime now) {
        return new Cart(null, guestToken, now);
    }

    public void touch(LocalDateTime now) {
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public UUID getGuestToken() {
        return guestToken;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
