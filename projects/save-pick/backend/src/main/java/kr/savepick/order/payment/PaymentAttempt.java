package kr.savepick.order.payment;

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
 * 10-erd.md payment_attempts 테이블. 주문 1건당 최대 3개의 결제 시도 기록(05-state-rules.md §4).
 * 상태 전이(REQUESTED → SUCCEEDED/FAILED)는 이 엔티티가 판정하지 않는다 — 판정은
 * {@code order/payment/PaymentAttemptExecutionService}가 하고, 이 엔티티는 결과를 담기만 한다.
 */
@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "attempt_no", nullable = false)
    private short attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentAttemptStatus status;

    @Column(name = "requested_amount", nullable = false)
    private int requestedAmount;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 30)
    private PaymentFailureReason failureReason;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    protected PaymentAttempt() {
    }

    private PaymentAttempt(Long orderId, short attemptNo, int requestedAmount, String idempotencyKey, LocalDateTime requestedAt) {
        this.orderId = orderId;
        this.attemptNo = attemptNo;
        this.status = PaymentAttemptStatus.REQUESTED;
        this.requestedAmount = requestedAmount;
        this.idempotencyKey = idempotencyKey;
        this.requestedAt = requestedAt;
    }

    /** API-022 5단계 — {@code attempt_no = payment_attempt_count + 1}로 만든다(BR-012). */
    public static PaymentAttempt request(Long orderId, short attemptNo, int requestedAmount, String idempotencyKey, LocalDateTime requestedAt) {
        return new PaymentAttempt(orderId, attemptNo, requestedAmount, idempotencyKey, requestedAt);
    }

    /** 가상 결제 판정 성공(BR-011). REQUESTED에서만 호출한다. */
    public void resolveSucceeded(LocalDateTime now) {
        this.status = PaymentAttemptStatus.SUCCEEDED;
        this.resolvedAt = now;
    }

    /** 가상 결제 판정 실패 또는 무응답(BR-011·012). REQUESTED에서만 호출한다. */
    public void resolveFailed(PaymentFailureReason failureReason, LocalDateTime now) {
        this.status = PaymentAttemptStatus.FAILED;
        this.failureReason = failureReason;
        this.resolvedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public short getAttemptNo() {
        return attemptNo;
    }

    public PaymentAttemptStatus getStatus() {
        return status;
    }

    public int getRequestedAmount() {
        return requestedAmount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public PaymentFailureReason getFailureReason() {
        return failureReason;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }
}
