package kr.savepick.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 10-erd.md login_attempts 테이블. 로그인 차단 판정의 근거 (12-auth.md §1.2).
 */
@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "member_id")
    private Long memberId;

    @Column(nullable = false)
    private boolean succeeded;

    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    protected LoginAttempt() {
    }

    private LoginAttempt(String email, Long memberId, boolean succeeded, LocalDateTime attemptedAt) {
        this.email = email;
        this.memberId = memberId;
        this.succeeded = succeeded;
        this.attemptedAt = attemptedAt;
    }

    public static LoginAttempt record(String email, Long memberId, boolean succeeded, LocalDateTime now) {
        return new LoginAttempt(email, memberId, succeeded, now);
    }

    public boolean isSucceeded() {
        return succeeded;
    }

    public LocalDateTime getAttemptedAt() {
        return attemptedAt;
    }
}
