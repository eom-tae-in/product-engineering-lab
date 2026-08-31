package kr.savepick.cart.api;

import java.util.UUID;

/**
 * API-013. {@code guestToken}은 요청에 게스트 토큰이 없어 서버가 새로 발급한 경우에만 채워진다
 * (기존 토큰을 그대로 썼거나 로그인 사용자면 null이다) — 클라이언트가 새 토큰을 저장하게 하기 위함이다.
 */
public record AddCartItemResponse(
        Long cartItemId, Long productId, int quantity, int currentPrice, int cartItemCount, UUID guestToken) {
}
