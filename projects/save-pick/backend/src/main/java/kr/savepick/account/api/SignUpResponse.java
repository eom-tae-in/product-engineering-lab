package kr.savepick.account.api;

import java.time.OffsetDateTime;
import kr.savepick.account.domain.Role;

public record SignUpResponse(
        Long memberId,
        String email,
        String name,
        Role role,
        String accessToken,
        OffsetDateTime accessTokenExpiresAt,
        boolean cartMerged) {
}
