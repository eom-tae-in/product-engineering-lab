/** kr.savepick.product.domain.ProductStatus와 1:1로 맞춘다(05 §5.1). */
export type ProductStatus = "DRAFT" | "ON_SALE" | "HIDDEN" | "CLOSED";

export interface PageInfo {
  number: number;
  size: number;
  totalElements: number;
}

/** docs/11-api-spec.md API-102 요청 파라미터. */
export interface AdminProductListParams {
  status?: ProductStatus;
  page?: number;
  size?: number;
}

/** docs/11-api-spec.md API-102 응답 `items[]` 원소. */
export interface AdminProductListItem {
  productId: number;
  name: string;
  status: ProductStatus;
  originalPrice: number;
  currentDiscountRate: number;
  currentPrice: number;
  nextDiscountRate: number;
  nextDiscountAt: string | null;
  closingAt: string;
  totalQuantity: number;
  availableQuantity: number;
}

/** docs/11-api-spec.md API-102 응답. */
export interface AdminProductListResponse {
  serverTime: string;
  items: AdminProductListItem[];
  page: PageInfo;
}

/** docs/11-api-spec.md API-103 요청. */
export interface CreateProductRequest {
  name: string;
  description: string;
  saleUnit: string;
  originalPrice: number;
  closingAt: string;
  maxOrderQuantity: number;
}

/** docs/11-api-spec.md API-103 응답 (201). */
export interface CreateProductResponse {
  productId: number;
  status: ProductStatus;
  name: string;
  originalPrice: number;
  closingAt: string;
  maxOrderQuantity: number;
}

export interface AdminProductStock {
  totalQuantity: number;
  availableQuantity: number;
  heldQuantity: number;
  confirmedQuantity: number;
  discardedQuantity: number;
}

/** docs/11-api-spec.md API-104 응답. */
export interface AdminProductDetailResponse {
  productId: number;
  name: string;
  description: string;
  saleUnit: string;
  originalPrice: number;
  closingAt: string;
  maxOrderQuantity: number;
  status: ProductStatus;
  currentDiscountRate: number;
  currentPrice: number;
  nextDiscountRate: number;
  nextDiscountAt: string | null;
  stock: AdminProductStock;
}

/** docs/11-api-spec.md API-105 요청. */
export interface UpdateProductRequest {
  name: string;
  description: string;
  saleUnit: string;
  originalPrice: number;
  closingAt: string;
  maxOrderQuantity: number;
  /** 확정 주문의 픽업 시간대보다 마감 시각이 빨라질 때만 true로 보낸다(FR-041 예외). */
  confirmEarlierClosing?: boolean;
}

/** docs/11-api-spec.md API-105 응답. */
export interface UpdateProductResponse {
  productId: number;
  originalPrice: number;
  closingAt: string;
  maxOrderQuantity: number;
  changedFields: string[];
  affectedConfirmedOrderCount: number;
  updatedAt: string;
}

/** docs/11-api-spec.md API-106 요청. */
export interface UpdateProductStatusRequest {
  status: ProductStatus;
}

/** docs/11-api-spec.md API-106 응답. */
export interface UpdateProductStatusResponse {
  productId: number;
  status: ProductStatus;
  changedAt: string;
  keptHoldCount: number;
  keptConfirmedOrderCount: number;
}

/** docs/11-api-spec.md API-108 응답 `tiers[]` 원소. */
export interface DiscountTier {
  code: string;
  condition: string;
  discountRate: number;
}

/** docs/11-api-spec.md API-108 응답. */
export interface DiscountPolicyResponse {
  tiers: DiscountTier[];
  rounding: string;
  minimumPrice: number;
  boundaryRule: string;
  editable: boolean;
}
