package kr.savepick.account.domain;

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
 * 10-erd.md members 테이블. 인증·프로필의 정본.
 */
@Entity
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_permission", nullable = false)
    private OrderPermission orderPermission;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Member() {
    }

    private Member(String email, String passwordHash, String name, String phone, Role role, LocalDateTime now) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.orderPermission = OrderPermission.ALLOWED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Member registerCustomer(String email, String passwordHash, String name, String phone, LocalDateTime now) {
        return new Member(email.toLowerCase(), passwordHash, name, phone, Role.CUSTOMER, now);
    }

    /**
     * 관리자 계정은 API로 만들지 않는다 (12-auth.md §5 P7) — 운영 스크립트·마이그레이션 전용 팩토리다.
     * 테스트에서 관리자 계정을 준비할 때도 이 메서드를 쓴다.
     */
    public static Member registerAdmin(String email, String passwordHash, String name, String phone, LocalDateTime now) {
        return new Member(email.toLowerCase(), passwordHash, name, phone, Role.ADMIN, now);
    }

    public void updateProfile(String name, String phone, LocalDateTime now) {
        this.name = name;
        this.phone = phone;
        this.updatedAt = now;
    }

    /**
     * BR-023 — 노쇼 3회 누적 제재. {@code members.order_permission}은 표시용 비정규화 값이다.
     * 판정의 정본은 {@code member_restrictions.ends_at}이며, 조회 시점에 경과 여부를 다시 판정한다
     * (11-api-spec.md BATCH-03 — "판정 시 ends_at &gt; now()를 확인하므로 시간이 지나면 자동으로
     * 풀린다", 05-state-rules.md §7.2). 그래서 7일 경과 후 이 값을 되돌리는 별도 배치를 두지 않는다.
     */
    public void restrict(LocalDateTime now) {
        this.orderPermission = OrderPermission.RESTRICTED;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public Role getRole() {
        return role;
    }

    public OrderPermission getOrderPermission() {
        return orderPermission;
    }
}
