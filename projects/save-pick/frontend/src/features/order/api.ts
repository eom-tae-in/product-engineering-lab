import { clientRequest } from "@/lib/api-client";
import type {
  AbandonOrderResponse,
  AssignPickupSlotResponse,
  CancelOrderResponse,
  HoldStatusResponse,
  OrderDetailResponse,
  OrderListResponse,
  OrderListStatusFilter,
  PaymentResponse,
  PickupSlotsResponse,
} from "./types";

/**
 * API-024 주문 상세 조회. 로그인 필수, 본인 주문만 조회된다. SC-005·SC-007이
 * 품목·확정 금액·픽업 시간대를 표시하기 위해 쓴다(types.ts 상단 주석 참고).
 */
export function fetchOrderDetail(orderId: number): Promise<OrderDetailResponse> {
  return clientRequest<OrderDetailResponse>(`/api/orders/${orderId}`, {
    method: "GET",
    authScope: "customer",
  });
}

/** API-018 선점 잔여 시간 조회. */
export function fetchOrderHold(orderId: number): Promise<HoldStatusResponse> {
  return clientRequest<HoldStatusResponse>(`/api/orders/${orderId}/hold`, {
    method: "GET",
    authScope: "customer",
  });
}

/** API-019 주문서 포기. 결제 전 포기는 EXPIRED로 전환되고 선점을 즉시 해제한다. */
export function abandonOrder(orderId: number): Promise<AbandonOrderResponse> {
  return clientRequest<AbandonOrderResponse>(`/api/orders/${orderId}`, {
    method: "DELETE",
    authScope: "customer",
  });
}

/**
 * API-020 선택 가능한 픽업 시간대 조회. `date`를 생략하면 선택 가능한 날짜별로
 * 모두 반환한다 — SC-006은 오늘·내일 탭을 클라이언트에서 전환하므로 한 번만 부른다.
 */
export function fetchPickupSlots(orderId: number): Promise<PickupSlotsResponse> {
  return clientRequest<PickupSlotsResponse>(`/api/orders/${orderId}/pickup-slots`, {
    method: "GET",
    authScope: "customer",
  });
}

/** API-021 픽업 시간대 지정. */
export function assignPickupSlot(
  orderId: number,
  slotId: number
): Promise<AssignPickupSlotResponse> {
  return clientRequest<AssignPickupSlotResponse>(`/api/orders/${orderId}/pickup-slot`, {
    method: "PATCH",
    authScope: "customer",
    body: { slotId },
  });
}

/**
 * API-022 가상 결제 요청. `Idempotency-Key`는 시도마다 새로 발급해야 한다(호출부가
 * `crypto.randomUUID()`로 만들어 넘긴다) — 같은 키로 재전송하면 서버가 이전 시도
 * 결과를 그대로 돌려주고 시도 횟수를 늘리지 않는다.
 */
export function submitPayment(
  orderId: number,
  amount: number,
  idempotencyKey: string
): Promise<PaymentResponse> {
  return clientRequest<PaymentResponse>(`/api/orders/${orderId}/payments`, {
    method: "POST",
    authScope: "customer",
    body: { amount },
    idempotencyKey,
  });
}

export interface FetchOrdersParams {
  status?: OrderListStatusFilter;
  /** true일 때만 EXPIRED·FAILED 주문을 포함한다(11번 API-023). SC-009 기본 목록은 생략한다. */
  includeExpired?: boolean;
}

/** API-023 주문 내역 조회. SC-009가 상태 필터·목록에 쓴다. */
export function fetchOrders(params: FetchOrdersParams = {}): Promise<OrderListResponse> {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  if (params.includeExpired) query.set("includeExpired", "true");
  const qs = query.toString();
  return clientRequest<OrderListResponse>(`/api/orders${qs ? `?${qs}` : ""}`, {
    method: "GET",
    authScope: "customer",
  });
}

/** API-025 주문 취소. 전체 취소만 지원하므로 `confirmed: true`를 항상 함께 보낸다(BR-024). */
export function cancelOrder(orderId: number): Promise<CancelOrderResponse> {
  return clientRequest<CancelOrderResponse>(`/api/orders/${orderId}/cancel`, {
    method: "POST",
    authScope: "customer",
    body: { confirmed: true },
  });
}
