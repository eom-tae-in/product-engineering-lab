package kr.savepick.account.infrastructure;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 12-auth.md §1.1 — bcrypt cost 12.
 */
@Component
public class BcryptPasswordHasher {

    private static final int COST = 12;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(COST);
    private final String dummyHash = encoder.encode("dummy-password-for-timing-safety");

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String hash) {
        return encoder.matches(rawPassword, hash);
    }

    /**
     * 미가입 이메일이어도 같은 비용의 비교 연산을 태워 응답 시간으로 가입 여부가 드러나지 않게 한다 (12번 §1.1).
     */
    public void compareAgainstDummy(String rawPassword) {
        encoder.matches(rawPassword, dummyHash);
    }
}
