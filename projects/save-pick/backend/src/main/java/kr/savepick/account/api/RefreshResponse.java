package kr.savepick.account.api;

import java.time.OffsetDateTime;

public record RefreshResponse(String accessToken, OffsetDateTime accessTokenExpiresAt) {
}
