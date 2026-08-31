package kr.savepick.account.api;

import kr.savepick.account.domain.OrderPermission;

public record MeResponse(Long memberId, String email, String name, String phone, OrderPermission orderPermission) {
}
