/**
 * docs/11-api-spec.md API-112~117 요청/응답 타입. SC-108(주문 목록, 관리자)·SC-109(픽업
 * 번호 조회)·SC-110(주문 상세, 관리자)이 공유한다.
 *
 * `OrderStatusValue`·`OrderActorType`은 `features/order/types.ts`(고객 도메인)와 값이
 * 완전히 같은 서버 enum이라 새로 정의하지 않고 그대로 가져다 쓴다 — 고객용 함수·타입은
 * 건드리지 않되, 같은 개념을 표현하는 타입을 두 번 정의하지 않기 위해서다.
 */
import type { OrderActorType, OrderStatusValue } from "@/features/order/types";

/** docs/11-api-spec.md API-112 응답 `items[]`. SC-108 주문 카드에 쓴다. */
export interface AdminOrderListItem {
  orderId: number;
  orderNo: string;
  pickupNumber: string | null;
  customerName: string;
  status: OrderStatusValue;
  pickupStartAt: string | null;
  pickupEndAt: string | null;
  noShowDueAt: string | null;
  totalAmount: number;
  itemCount: number;
}

/** docs/11-api-spec.md API-112 응답. */
export interface AdminOrderListResponse {
  items: AdminOrderListItem[];
  page: { number: number; size: number; totalElements: number };
}

/**
 * docs/11-api-spec.md API-112 `status` 파라미터 중 이 화면(SC-108)이 필터로 노출하는
 * 값. PENDING·EXPIRED는 `wireframes/sc-108-admin-order-list.html`의 상태 select에도
 * 없다 — FR-048이 "기본 목록에 넣지 않는다"고만 규정해 명시적으로 조회할 수 있는지는
 * 11번에 남아 있지만(§API-112 설명), 이 화면의 필터 목록에는 두지 않는다.
 */
export type AdminOrderStatusFilter =
  | "CONFIRMED"
  | "READY"
  | "COMPLETED"
  | "CANCELED"
  | "NO_SHOW"
  | "FAILED";

export interface AdminOrderCustomer {
  name: string;
  phone: string;
}

export interface AdminOrderDetailItem {
  productId: number;
  name: string;
  quantity: number;
  unitPrice: number;
  lineAmount: number;
  /** 주문 시점 스냅샷. 취소 시 재고 복구 여부가 이 시각으로 갈린다(BR-019). */
  productClosingAt: string;
}

export type PaymentAttemptStatus = "SUCCEEDED" | "FAILED";

export interface AdminOrderPaymentAttempt {
  attemptNo: number;
  status: PaymentAttemptStatus;
  failureReason?: string;
  requestedAt: string;
  resolvedAt: string;
}

export interface AdminOrderStatusHistoryEntry {
  fromStatus: OrderStatusValue | null;
  toStatus: OrderStatusValue;
  actorType: OrderActorType;
  occurredAt: string;
}

/** docs/11-api-spec.md API-114 응답 `availableActions[]`. */
export type AdminOrderAction = "READY" | "COMPLETE" | "CANCEL";

/**
 * docs/11-api-spec.md API-114 응답. API-113(픽업 번호 조회)도 "API-114와 동일한 주문
 * 상세 객체"라고 명시하므로 같은 타입을 쓴다.
 *
 * `pickupStartAt`·`pickupEndAt`은 06번 SC-109·SC-110의 "픽업 날짜·시간대" 표시에 쓴다.
 * 시각은 주문이 아니라 지정된 픽업 시간대에 있어 아직 시간대를 고르지 않은 주문에서는
 * null이다(11번 API-114 설명, API-112의 같은 필드와 같은 규칙).
 */
export interface AdminOrderDetailResponse {
  orderId: number;
  orderNo: string;
  status: OrderStatusValue;
  pickupNumber: string | null;
  pickupStartAt: string | null;
  pickupEndAt: string | null;
  customer: AdminOrderCustomer;
  items: AdminOrderDetailItem[];
  totalAmount: number;
  paymentAttempts: AdminOrderPaymentAttempt[];
  statusHistory: AdminOrderStatusHistoryEntry[];
  availableActions: AdminOrderAction[];
}

/** docs/11-api-spec.md API-115 응답. */
export interface AdminOrderReadyResponse {
  orderId: number;
  status: OrderStatusValue;
  readyAt: string;
  stockChanged: boolean;
}

/** docs/11-api-spec.md API-116 응답. */
export interface AdminOrderCompleteResponse {
  orderId: number;
  status: OrderStatusValue;
  completedAt: string;
  stockChanged: boolean;
}

/** docs/11-api-spec.md API-117 응답 `stockResults[]`. `features/order/types.ts`의 `CancelOrderStockResult`와 구조가 같다. */
export interface AdminOrderCancelStockResult {
  productId: number;
  quantity: number;
  restored: boolean;
  reason: "CANCEL_RESTORE" | "CANCEL_DISCARD";
}

/** docs/11-api-spec.md API-117 응답. */
export interface AdminOrderCancelResponse {
  orderId: number;
  status: OrderStatusValue;
  canceledBy: OrderActorType;
  cancelReason: string;
  canceledAt: string;
  slotReleased: boolean;
  stockResults: AdminOrderCancelStockResult[];
}
