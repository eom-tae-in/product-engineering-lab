import { clientRequest } from "@/lib/api-client";
import type {
  AddCartItemResponse,
  CartResponse,
  CreateOrderResponse,
  RemoveUnavailableCartItemsResponse,
  UpdateCartItemResponse,
} from "./types";

/**
 * API-013 장바구니 담기. 비로그인(게스트 토큰) 또는 고객 모두 쓸 수 있다.
 */
export function addToCart(productId: number, quantity: number): Promise<AddCartItemResponse> {
  return clientRequest<AddCartItemResponse>("/api/cart/items", {
    method: "POST",
    authScope: "customer",
    useGuestToken: true,
    body: { productId, quantity },
  });
}

/**
 * API-012 장바구니 조회 (유효성 재검증 포함). SC-004는 비로그인도 볼 수 있어
 * `useGuestToken: true`로 게스트 토큰을 함께 보낸다(로그인 상태면 액세스 토큰이
 * 우선 적용된다).
 */
export function fetchCart(): Promise<CartResponse> {
  return clientRequest<CartResponse>("/api/cart", {
    method: "GET",
    authScope: "customer",
    useGuestToken: true,
  });
}

/**
 * API-014 장바구니 수량 변경. `quantity`가 0이면 서버가 품목을 삭제하고 204(본문
 * 없음)를 반환한다 — 호출부가 이 경우를 구분해서 처리해야 한다.
 */
export function updateCartItemQuantity(
  cartItemId: number,
  quantity: number
): Promise<UpdateCartItemResponse> {
  return clientRequest<UpdateCartItemResponse>(`/api/cart/items/${cartItemId}`, {
    method: "PATCH",
    authScope: "customer",
    useGuestToken: true,
    body: { quantity },
  });
}

/** API-015 장바구니 품목 삭제. 성공하면 204(본문 없음)를 반환한다. */
export function removeCartItem(cartItemId: number): Promise<void> {
  return clientRequest<void>(`/api/cart/items/${cartItemId}`, {
    method: "DELETE",
    authScope: "customer",
    useGuestToken: true,
  });
}

/** API-016 구매 불가 품목 일괄 삭제. */
export function removeUnavailableCartItems(): Promise<RemoveUnavailableCartItemsResponse> {
  return clientRequest<RemoveUnavailableCartItemsResponse>("/api/cart/items/unavailable", {
    method: "DELETE",
    authScope: "customer",
    useGuestToken: true,
  });
}

/**
 * API-017 주문서 생성 (재고 임시 선점). 로그인 필수라 게스트 토큰은 붙이지 않는다.
 * `cartItemIds`는 구매 가능한 품목만 골라 보낸다(SC-004 `주문하기`).
 */
export function createOrder(cartItemIds: number[]): Promise<CreateOrderResponse> {
  return clientRequest<CreateOrderResponse>("/api/orders", {
    method: "POST",
    authScope: "customer",
    body: { cartItemIds },
  });
}
