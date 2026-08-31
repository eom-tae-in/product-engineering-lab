package kr.savepick.account.api;

import java.time.OffsetDateTime;
import kr.savepick.account.domain.OrderPermission;
import kr.savepick.account.domain.Role;

public record LoginResponse(
        Long memberId,
        String name,
        Role role,
        String accessToken,
        OffsetDateTime accessTokenExpiresAt,
        OrderPermission orderPermission) {
}
