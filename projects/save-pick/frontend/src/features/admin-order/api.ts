import { clientRequest } from "@/lib/api-client";
import type {
  AdminOrderCancelResponse,
  AdminOrderCompleteResponse,
  AdminOrderDetailResponse,
  AdminOrderListResponse,
  AdminOrderReadyResponse,
  AdminOrderStatusFilter,
} from "./types";

export interface FetchAdminOrdersParams {
  /** `YYYY-MM-DD`. 생략하면 서버 기본값(오늘·내일)을 쓴다(API-112). */
  pickupDate?: string;
  status?: AdminOrderStatusFilter;
}

/** API-112 주문 목록 조회. SC-108이 쓴다. */
export function fetchAdminOrders(
  params: FetchAdminOrdersParams = {}
): Promise<AdminOrderListResponse> {
  const query = new URLSearchParams();
  if (params.pickupDate) query.set("pickupDate", params.pickupDate);
  if (params.status) query.set("status", params.status);
  const qs = query.toString();
  return clientRequest<AdminOrderListResponse>(`/api/admin/orders${qs ? `?${qs}` : ""}`, {
    method: "GET",
    authScope: "admin",
  });
}

/**
 * API-113 픽업 번호로 주문 조회. SC-109가 쓴다. `businessDate`를 생략하면 오늘
 * 영업일로 조회한다(FR-049) — SC-109는 날짜 선택 UI가 없으므로 항상 생략한다.
 */
export function fetchOrderByPickupNumber(pickupNumber: string): Promise<AdminOrderDetailResponse> {
  const query = new URLSearchParams({ pickupNumber });
  return clientRequest<AdminOrderDetailResponse>(
    `/api/admin/orders/by-pickup-number?${query.toString()}`,
    { method: "GET", authScope: "admin" }
  );
}

/** API-114 주문 상세 조회(관리자). SC-110이 쓴다. */
export function fetchAdminOrderDetail(orderId: number): Promise<AdminOrderDetailResponse> {
  return clientRequest<AdminOrderDetailResponse>(`/api/admin/orders/${orderId}`, {
    method: "GET",
    authScope: "admin",
  });
}

/** API-115 픽업 준비 완료 처리. SC-110이 쓴다. */
export function markOrderReady(orderId: number): Promise<AdminOrderReadyResponse> {
  return clientRequest<AdminOrderReadyResponse>(`/api/admin/orders/${orderId}/ready`, {
    method: "POST",
    authScope: "admin",
  });
}

/** API-116 픽업 완료 처리. SC-109·SC-110이 쓴다. */
export function completeOrderPickup(orderId: number): Promise<AdminOrderCompleteResponse> {
  return clientRequest<AdminOrderCompleteResponse>(`/api/admin/orders/${orderId}/complete`, {
    method: "POST",
    authScope: "admin",
  });
}

/** API-117 관리자 주문 취소. SC-110이 쓴다. 사유는 필수다(BR-020, FR-054). */
export function cancelAdminOrder(
  orderId: number,
  reason: string
): Promise<AdminOrderCancelResponse> {
  return clientRequest<AdminOrderCancelResponse>(`/api/admin/orders/${orderId}/cancel`, {
    method: "POST",
    authScope: "admin",
    body: { reason },
  });
}
