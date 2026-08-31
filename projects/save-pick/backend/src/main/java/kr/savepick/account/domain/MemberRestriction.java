package kr.savepick.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 10-erd.md member_restrictions 테이블. BR-023 노쇼 누적 제재 이력.
 * 생성은 order 도메인의 BATCH-03(NoShowDetectionJob)이 이 서비스({@code OrderRestrictionService})를
 * 통해 호출한다(14-project-structure.md §6.1 — "제재 생성은 account 서비스를 호출한다").
 */
@Entity
@Table(name = "member_restrictions")
public class MemberRestriction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private String reason;

    @Column(name = "trigger_order_id", nullable = false)
    private Long triggerOrderId;

    @Column(name = "triggered_no_show_count", nullable = false)
    private short triggeredNoShowCount;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    protected MemberRestriction() {
    }

    private MemberRestriction(Long memberId, Long triggerOrderId, short triggeredNoShowCount, LocalDateTime startedAt, LocalDateTime endsAt) {
        this.memberId = memberId;
        this.reason = "NO_SHOW_ACCUMULATION";
        this.triggerOrderId = triggerOrderId;
        this.triggeredNoShowCount = triggeredNoShowCount;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
    }

    /**
     * BATCH-03 — 최근 30일 노쇼가 정확히 {@code triggeredNoShowCount}(항상 3, CHK 제약)에 도달한
     * 시점에만 만든다. {@code UQ_member_restrictions_member_trigger}가 같은 사건의 중복 생성을 막는다.
     */
    public static MemberRestriction create(Long memberId, Long triggerOrderId, short triggeredNoShowCount, LocalDateTime startedAt, LocalDateTime endsAt) {
        return new MemberRestriction(memberId, triggerOrderId, triggeredNoShowCount, startedAt, endsAt);
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getTriggerOrderId() {
        return triggerOrderId;
    }

    public short getTriggeredNoShowCount() {
        return triggeredNoShowCount;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }
}
