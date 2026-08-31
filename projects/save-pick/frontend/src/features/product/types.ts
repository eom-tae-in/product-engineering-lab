/** docs/11-api-spec.md API-010 요청 파라미터. */
export type ProductSort = "CLOSING_SOON" | "DISCOUNT_DESC" | "PRICE_ASC";

export interface ProductListParams {
  keyword?: string;
  sort?: ProductSort;
  hideSoldOut?: boolean;
  page?: number;
  size?: number;
}

export interface PageInfo {
  number: number;
  size: number;
  totalElements: number;
}

/** docs/11-api-spec.md API-010 응답의 `items[]` 원소. */
export interface ProductListItem {
  productId: number;
  name: string;
  saleUnit: string;
  originalPrice: number;
  discountRate: number;
  discountPrice: number;
  availableQuantity: number;
  lowStock: boolean;
  soldOut: boolean;
  closingAt: string;
  nextDiscountAt: string | null;
}

/** docs/11-api-spec.md API-010 응답. */
export interface ProductListResponse {
  serverTime: string;
  items: ProductListItem[];
  page: PageInfo;
}

/** docs/11-api-spec.md API-011 응답. */
export interface ProductDetailResponse {
  serverTime: string;
  productId: number;
  name: string;
  description: string;
  saleUnit: string;
  originalPrice: number;
  discountRate: number;
  discountPrice: number;
  availableQuantity: number;
  lowStock: boolean;
  soldOut: boolean;
  maxOrderQuantity: number;
  closingAt: string;
  purchasable: boolean;
}
