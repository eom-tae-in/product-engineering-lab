/** docs/11-api-spec.md 응답 공통 페이지 정보. */
export interface PageInfo {
  number: number;
  size: number;
  totalElements: number;
}

/** kr.savepick.product.domain.ProductStatus(05 §5.1)와 1:1. API-110 items[].status. */
export type ProductStatus = "DRAFT" | "ON_SALE" | "HIDDEN" | "CLOSED";

/** docs/11-api-spec.md API-110 요청 파라미터. */
export interface StockListParams {
  onlyUnavailable?: boolean;
  page?: number;
  size?: number;
}

/** docs/11-api-spec.md API-110 응답 `items[]` 원소. */
export interface StockListItem {
  productId: number;
  name: string;
  status: ProductStatus;
  totalQuantity: number;
  availableQuantity: number;
  heldQuantity: number;
  confirmedQuantity: number;
  discardedQuantity: number;
  /**
   * `totalQuantity = availableQuantity + heldQuantity + confirmedQuantity` 성립 여부.
   * docs/06-screen-list.md SC-105 표시 규칙에 없어 화면에 노출하지 않는다(작업 지시 반영).
   */
  consistent: boolean;
}

/** docs/11-api-spec.md API-110 응답. */
export interface StockListResponse {
  serverTime: string;
  items: StockListItem[];
  page: PageInfo;
}

/** docs/11-api-spec.md API-109 요청. 총 재고의 절대값(목표값)을 보낸다(증감량 아님). */
export interface AdjustStockRequest {
  totalQuantity: number;
  note?: string;
}

/** docs/11-api-spec.md API-109 응답의 `before`/`after` 스냅샷. */
export interface StockSnapshot {
  totalQuantity: number;
  availableQuantity: number;
  heldQuantity: number;
  confirmedQuantity: number;
}

/** docs/11-api-spec.md API-109 응답 (200). */
export interface AdjustStockResponse {
  productId: number;
  before: StockSnapshot;
  after: StockSnapshot;
  /** BR-025 하한선(`heldQuantity + confirmedQuantity`). `STOCK_BELOW_COMMITTED` 오류의 `details`에도 같은 이름으로 온다. */
  minimumSettableQuantity: number;
  changedAt: string;
}

/**
 * docs/11-api-spec.md API-111 응답 `items[].reason`.
 * docs/06-screen-list.md SC-106 "사유 표기 대응표" 7종과 1:1 대응한다.
 */
export type StockLedgerReason =
  | "ADMIN_ADJUST"
  | "HOLD"
  | "CONFIRM"
  | "HOLD_RELEASE"
  | "HOLD_EXPIRE"
  | "CANCEL_RESTORE"
  | "CANCEL_DISCARD";

export type StockLedgerActorType = "CUSTOMER" | "ADMIN" | "SYSTEM";

/** docs/11-api-spec.md API-111 요청 파라미터. */
export interface StockLedgerListParams {
  page?: number;
  size?: number;
}

/**
 * docs/11-api-spec.md API-111 응답 `items[]` 원소.
 * `deltaTotal`/`deltaHeld`/`deltaConfirmed`, `afterTotal`/`afterAvailable`는 그 이력이
 * 실제로 바꾼 값만 담겨 온다(예시 응답에서 사유별로 실려 있는 필드가 다르다).
 */
export interface StockLedgerItem {
  ledgerId: number;
  reason: StockLedgerReason;
  orderNo: string | null;
  deltaTotal?: number;
  deltaHeld?: number;
  deltaConfirmed?: number;
  afterTotal?: number;
  afterAvailable?: number;
  actorType: StockLedgerActorType;
  note?: string | null;
  occurredAt: string;
}

/** docs/11-api-spec.md API-111 응답. */
export interface StockLedgerListResponse {
  items: StockLedgerItem[];
  page: PageInfo;
}
