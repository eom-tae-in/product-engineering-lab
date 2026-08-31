import { clientRequest } from "@/lib/api-client";
import type {
  AdminProductDetailResponse,
  AdminProductListParams,
  AdminProductListResponse,
  CreateProductRequest,
  CreateProductResponse,
  DiscountPolicyResponse,
  ProductStatus,
  UpdateProductRequest,
  UpdateProductResponse,
  UpdateProductStatusResponse,
} from "./types";

function buildQuery(params: object): string {
  const query = new URLSearchParams();
  Object.entries(params as Record<string, string | number | undefined>).forEach(
    ([key, value]) => {
      if (value !== undefined) query.set(key, String(value));
    }
  );
  const qs = query.toString();
  return qs ? `?${qs}` : "";
}

/** API-102 상품 목록 조회(관리자). 관리자 전용(SC-103). */
export function fetchAdminProducts(
  params: AdminProductListParams = {}
): Promise<AdminProductListResponse> {
  return clientRequest<AdminProductListResponse>(`/api/admin/products${buildQuery(params)}`, {
    authScope: "admin",
  });
}

/** API-103 상품 등록. 관리자 전용(SC-104 등록). */
export function createProduct(request: CreateProductRequest): Promise<CreateProductResponse> {
  return clientRequest<CreateProductResponse>("/api/admin/products", {
    method: "POST",
    authScope: "admin",
    body: request,
  });
}

/** API-104 상품 상세 조회(관리자). 관리자 전용(SC-104 수정). */
export function fetchAdminProductDetail(
  productId: number | string
): Promise<AdminProductDetailResponse> {
  return clientRequest<AdminProductDetailResponse>(`/api/admin/products/${productId}`, {
    authScope: "admin",
  });
}

/** API-105 상품 수정. 관리자 전용(SC-104 수정). */
export function updateProduct(
  productId: number | string,
  request: UpdateProductRequest
): Promise<UpdateProductResponse> {
  return clientRequest<UpdateProductResponse>(`/api/admin/products/${productId}`, {
    method: "PATCH",
    authScope: "admin",
    body: request,
  });
}

/** API-106 상품 판매 상태 전환. 관리자 전용(SC-103). */
export function updateProductStatus(
  productId: number | string,
  status: ProductStatus
): Promise<UpdateProductStatusResponse> {
  return clientRequest<UpdateProductStatusResponse>(`/api/admin/products/${productId}/status`, {
    method: "PATCH",
    authScope: "admin",
    body: { status },
  });
}

/** API-108 할인 구간 정책 조회. 관리자 전용(SC-107). */
export function fetchDiscountPolicy(): Promise<DiscountPolicyResponse> {
  return clientRequest<DiscountPolicyResponse>("/api/admin/discount-policy", {
    authScope: "admin",
  });
}
