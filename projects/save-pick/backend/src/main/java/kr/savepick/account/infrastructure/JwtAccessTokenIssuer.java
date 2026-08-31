package kr.savepick.account.infrastructure;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import kr.savepick.account.domain.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 12-auth.md §1.3 — 액세스 토큰은 JWT(HS256), 클레임 sub/role/sid/iat/exp/typ.
 */
@Component
public class JwtAccessTokenIssuer {

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final ZoneId zone;

    public JwtAccessTokenIssuer(
            @Value("${savepick.auth.jwt-secret}") String secret,
            @Value("${savepick.auth.access-token-ttl}") String accessTokenTtl,
            @Value("${savepick.time-zone:Asia/Seoul}") String timeZone) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.parse(accessTokenTtl);
        this.zone = ZoneId.of(timeZone);
    }

    public IssuedAccessToken issue(Long memberId, Role role, UUID sessionId, LocalDateTime now) {
        LocalDateTime expiresAtLdt = now.plus(accessTokenTtl);
        String token = Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("role", role.name())
                .claim("sid", sessionId.toString())
                .claim("typ", "access")
                .issuedAt(toDate(now))
                .expiration(toDate(expiresAtLdt))
                .signWith(key)
                .compact();
        return new IssuedAccessToken(token, expiresAtLdt);
    }

    public ParsedAccessToken parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!"access".equals(claims.get("typ", String.class))) {
                return ParsedAccessToken.invalid();
            }
            Long memberId = Long.valueOf(claims.getSubject());
            Role role = Role.valueOf(claims.get("role", String.class));
            UUID sessionId = UUID.fromString(claims.get("sid", String.class));
            return ParsedAccessToken.valid(memberId, role, sessionId);
        } catch (JwtException | IllegalArgumentException e) {
            return ParsedAccessToken.invalid();
        }
    }

    private Date toDate(LocalDateTime ldt) {
        return Date.from(ldt.atZone(zone).toInstant());
    }

    public record IssuedAccessToken(String token, LocalDateTime expiresAt) {
    }

    public record ParsedAccessToken(boolean valid, Long memberId, Role role, UUID sessionId) {
        public static ParsedAccessToken valid(Long memberId, Role role, UUID sessionId) {
            return new ParsedAccessToken(true, memberId, role, sessionId);
        }

        public static ParsedAccessToken invalid() {
            return new ParsedAccessToken(false, null, null, null);
        }
    }
}
