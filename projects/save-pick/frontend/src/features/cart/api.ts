import { clientRequest } from "@/lib/api-client";
import type { AddCartItemResponse } from "./types";

/**
 * API-013 장바구니 담기. 비로그인(게스트 토큰) 또는 고객 모두 쓸 수 있다.
 * 이번 슬라이스는 SC-003(상품 상세)의 담기 액션만 다룬다 — 장바구니 도메인 전체
 * (조회·수량 변경·삭제 등)는 다음 슬라이스에서 features/cart를 확장한다.
 */
export function addToCart(productId: number, quantity: number): Promise<AddCartItemResponse> {
  return clientRequest<AddCartItemResponse>("/api/cart/items", {
    method: "POST",
    authScope: "customer",
    useGuestToken: true,
    body: { productId, quantity },
  });
}
