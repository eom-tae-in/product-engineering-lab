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

/** docs/11-api-spec.md API-012: 구매 불가 사유. */
export type CartUnavailableReason = "OUT_OF_STOCK" | "PRODUCT_CLOSED" | "PRODUCT_NOT_ON_SALE";

/** docs/11-api-spec.md API-012 응답의 `items[]` 원소. */
export interface CartItem {
  cartItemId: number;
  productId: number;
  name: string;
  quantity: number;
  addedPrice: number;
  currentPrice: number;
  priceChanged: boolean;
  lineAmount: number;
  availableQuantity: number;
  shortage: number;
  purchasable: boolean;
  unavailableReason: CartUnavailableReason | null;
}

/** docs/11-api-spec.md API-012 · 장바구니 조회 (유효성 재검증 포함) 응답. */
export interface CartResponse {
  serverTime: string;
  guestToken: string;
  items: CartItem[];
  totalAmount: number;
  orderable: boolean;
}

/** docs/11-api-spec.md API-014 요청. `quantity: 0`이면 삭제로 처리한다(204 응답). */
export interface UpdateCartItemRequest {
  quantity: number;
}

/** docs/11-api-spec.md API-014 응답 (200). */
export interface UpdateCartItemResponse {
  cartItemId: number;
  quantity: number;
  lineAmount: number;
  totalAmount: number;
}

/** docs/11-api-spec.md API-016 · 구매 불가 품목 일괄 삭제 응답. */
export interface RemoveUnavailableCartItemsResponse {
  removedCartItemIds: number[];
  remainingItemCount: number;
  totalAmount: number;
  orderable: boolean;
}

/**
 * docs/11-api-spec.md API-017 · 주문서 생성 요청.
 * 지정하지 않으면 서버가 장바구니 전 품목을 대상으로 하지만, SC-004는 구매 가능한
 * 품목만 골라 보낸다(구매 불가 품목이 있으면 애초에 `주문하기` 버튼이 비활성이다).
 */
export interface CreateOrderRequest {
  cartItemIds: number[];
}

export interface CreateOrderItem {
  productId: number;
  name: string;
  quantity: number;
  originalUnitPrice: number;
  discountRate: number;
  unitPrice: number;
  lineAmount: number;
  productClosingAt: string;
}

/** docs/11-api-spec.md API-017 응답 (201). */
export interface CreateOrderResponse {
  orderId: number;
  orderNo: string;
  status: string;
  serverTime: string;
  holdExpiresAt: string;
  holdRemainingSeconds: number;
  paymentAttemptRemaining: number;
  totalAmount: number;
  items: CreateOrderItem[];
  earliestClosingAt: string;
}
