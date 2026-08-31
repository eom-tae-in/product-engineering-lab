package kr.savepick.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 10-erd.md auth_sessions 테이블. 리프레시 토큰의 서버 상태 (12-auth.md §1.3 S5).
 */
@Entity
@Table(name = "auth_sessions")
public class AuthSession {

    @Id
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64)
    private String refreshTokenHash;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "user_agent")
    private String userAgent;

    protected AuthSession() {
    }

    private AuthSession(Long memberId, String refreshTokenHash, LocalDateTime issuedAt, LocalDateTime expiresAt, String userAgent) {
        this.id = UUID.randomUUID();
        this.memberId = memberId;
        this.refreshTokenHash = refreshTokenHash;
        this.issuedAt = issuedAt;
        this.lastUsedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
    }

    public static AuthSession issue(Long memberId, String refreshTokenHash, LocalDateTime now, LocalDateTime expiresAt, String userAgent) {
        return new AuthSession(memberId, refreshTokenHash, now, expiresAt, userAgent);
    }

    /** 12-auth.md §2.2 — 재발급 시 회전: 같은 행의 해시를 교체하고 수명을 연장한다. */
    public void rotate(String newRefreshTokenHash, LocalDateTime now, LocalDateTime newExpiresAt) {
        this.refreshTokenHash = newRefreshTokenHash;
        this.lastUsedAt = now;
        this.expiresAt = newExpiresAt;
    }

    public void revoke(LocalDateTime now) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public boolean isUsable(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public UUID getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }
}
