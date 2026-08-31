package kr.savepick.account.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** API-006 (11-api-spec.md). 이메일은 받지 않는다 — 수정 대상이 아니다 (FR-003). */
public record UpdateMeRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "^01\\d{8,9}$", message = "휴대폰 번호 형식이 올바르지 않습니다.") String phone) {
}
