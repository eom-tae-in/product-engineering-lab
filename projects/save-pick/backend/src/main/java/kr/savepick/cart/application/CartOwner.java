package kr.savepick.cart.application;

import java.util.UUID;

/**
 * 12-auth.md §3.3 — 로그인 시 {@code member_id = 토큰 sub}, 비로그인 시
 * {@code guest_token = X-Guest-Token} 헤더값으로 장바구니 소유자를 식별한다.
 * 요청 처리 전 구간에서 이 값 하나로만 소유권을 판정해, 컨트롤러가 직접 판정하지 않게 한다.
 */
public record CartOwner(Long memberId, UUID guestToken) {

    public static CartOwner ofMember(Long memberId) {
        return new CartOwner(memberId, null);
    }

    public static CartOwner ofGuest(UUID guestToken) {
        return new CartOwner(null, guestToken);
    }

    public boolean isGuest() {
        return memberId == null;
    }
}
