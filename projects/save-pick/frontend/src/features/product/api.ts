import { serverGet } from "@/lib/api-client";
import type { ProductDetailResponse, ProductListParams, ProductListResponse } from "./types";

function buildQuery(params: object): string {
  const query = new URLSearchParams();
  Object.entries(params as Record<string, string | number | boolean | undefined>).forEach(
    ([key, value]) => {
      if (value !== undefined) query.set(key, String(value));
    }
  );
  const qs = query.toString();
  return qs ? `?${qs}` : "";
}

/**
 * API-010 상품 목록 조회(검색·정렬·필터 포함). 비로그인도 볼 수 있는 조회 화면
 * (SC-001·SC-002)이라 Server Component에서 serverGet으로 직접 호출한다
 * (ARCHITECTURE.md 데이터 페칭 규칙).
 */
export function fetchProducts(params: ProductListParams = {}): Promise<ProductListResponse> {
  return serverGet<ProductListResponse>(`/api/products${buildQuery(params)}`);
}

/** API-011 상품 상세 조회. SC-003. */
export function fetchProductDetail(productId: string | number): Promise<ProductDetailResponse> {
  return serverGet<ProductDetailResponse>(`/api/products/${productId}`);
}
