package kr.savepick.account.api;

import jakarta.validation.constraints.NotBlank;

/** API-002, API-101 공용 (11-api-spec.md). */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password,
        String guestToken) {
}
