package kr.savepick.account.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** API-001 (11-api-spec.md). */
public record SignUpRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "^01\\d{8,9}$", message = "휴대폰 번호 형식이 올바르지 않습니다.") String phone,
        String guestToken) {
}
