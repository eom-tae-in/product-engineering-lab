package kr.savepick.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 10-erd.md orders 테이블. 주문 1건의 정본 (05-state-rules.md §2).
 * 상태 전이는 이 엔티티의 세터가 아니라 {@link OrderTransitionRule}의 판정을 거쳐
 * {@code order/infrastructure/OrderJpaRepository}의 조건부 UPDATE로만 실행한다
 * (14-project-structure.md §9.2, §5 강제 규칙 2와 같은 패턴). 픽업 시간대 지정(pickupSlotId·
 * pickupBusinessDate)처럼 상태를 바꾸지 않는 속성 갱신은 이 엔티티의 메서드 + 통상적인 저장으로
 * 처리한다(Product의 비상태 필드 갱신과 같은 패턴).
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 20)
    private String orderNo;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    @Column(name = "contact_name", nullable = false, length = 50)
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 20)
    private String contactPhone;

    @Column(name = "pickup_slot_id")
    private Long pickupSlotId;

    @Column(name = "pickup_business_date")
    private LocalDate pickupBusinessDate;

    @Column(name = "pickup_number")
    private Short pickupNumber;

    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    @Column(name = "payment_attempt_count", nullable = false)
    private short paymentAttemptCount;

    @Column(name = "cancelable_until")
    private LocalDateTime cancelableUntil;

    @Column(name = "no_show_due_at")
    private LocalDateTime noShowDueAt;

    @Column(name = "stock_settled_at")
    private LocalDateTime stockSettledAt;

    @Column(name = "canceled_by", length = 10)
    private String canceledBy;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "no_show_at")
    private LocalDateTime noShowAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Order() {
    }

    private Order(
            String orderNo, Long memberId, int totalAmount, String contactName, String contactPhone,
            LocalDateTime holdExpiresAt, LocalDateTime now) {
        this.orderNo = orderNo;
        this.memberId = memberId;
        this.status = OrderStatus.PENDING;
        this.totalAmount = totalAmount;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.holdExpiresAt = holdExpiresAt;
        this.paymentAttemptCount = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** API-017 — 재고 선점이 성공한 뒤에만 호출한다(BR-007, BR-027). */
    public static Order createPending(
            String orderNo, Long memberId, int totalAmount, String contactName, String contactPhone,
            LocalDateTime holdExpiresAt, LocalDateTime now) {
        return new Order(orderNo, memberId, totalAmount, contactName, contactPhone, holdExpiresAt, now);
    }

    /**
     * {@code InventoryHoldService.createHolds}가 실제로 사용한 만료 시각으로 맞춘다 — orders와
     * inventory_holds의 {@code expires_at}이 반드시 같아야 한다(과제 지시, 13번 §3).
     */
    public void correctHoldExpiresAt(LocalDateTime holdExpiresAt, LocalDateTime now) {
        this.holdExpiresAt = holdExpiresAt;
        this.updatedAt = now;
    }

    /** API-021 — 이 시점에는 픽업 시간대 정원을 점유하지 않는다(05 §8, A10). */
    public void assignPickupSlot(Long pickupSlotId, LocalDate pickupBusinessDate, LocalDateTime now) {
        this.pickupSlotId = pickupSlotId;
        this.pickupBusinessDate = pickupBusinessDate;
        this.updatedAt = now;
    }

    public boolean isPending() {
        return status == OrderStatus.PENDING;
    }

    /** BR-007·008 — {@code now >= holdExpiresAt}이면 만료다. PENDING이 아니면 항상 false다. */
    public boolean isHoldExpired(LocalDateTime now) {
        return isPending() && holdExpiresAt != null && !now.isBefore(holdExpiresAt);
    }

    public Long getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getMemberId() {
        return memberId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public int getTotalAmount() {
        return totalAmount;
    }

    public String getContactName() {
        return contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public Long getPickupSlotId() {
        return pickupSlotId;
    }

    public LocalDate getPickupBusinessDate() {
        return pickupBusinessDate;
    }

    public Short getPickupNumber() {
        return pickupNumber;
    }

    public LocalDateTime getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public short getPaymentAttemptCount() {
        return paymentAttemptCount;
    }

    public LocalDateTime getCancelableUntil() {
        return cancelableUntil;
    }

    public LocalDateTime getNoShowDueAt() {
        return noShowDueAt;
    }

    public LocalDateTime getStockSettledAt() {
        return stockSettledAt;
    }

    public String getCanceledBy() {
        return canceledBy;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public LocalDateTime getReadyAt() {
        return readyAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCanceledAt() {
        return canceledAt;
    }

    public LocalDateTime getNoShowAt() {
        return noShowAt;
    }

    public LocalDateTime getExpiredAt() {
        return expiredAt;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
