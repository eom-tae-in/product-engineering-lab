/** docs/11-api-spec.md API-013 요청. */
export interface AddCartItemRequest {
  productId: number;
  quantity: number;
}

/** docs/11-api-spec.md API-013 응답 (201). */
export interface AddCartItemResponse {
  cartItemId: number;
  productId: number;
  quantity: number;
  currentPrice: number;
  cartItemCount: number;
}
