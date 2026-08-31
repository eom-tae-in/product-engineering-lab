package kr.savepick.account.api;

public record UpdateMeResponse(Long memberId, String email, String name, String phone) {
}
