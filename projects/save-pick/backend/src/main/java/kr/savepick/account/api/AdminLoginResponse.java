package kr.savepick.account.api;

import java.time.OffsetDateTime;
import kr.savepick.account.domain.Role;

public record AdminLoginResponse(
        Long memberId,
        String name,
        Role role,
        String accessToken,
        OffsetDateTime accessTokenExpiresAt) {
}
