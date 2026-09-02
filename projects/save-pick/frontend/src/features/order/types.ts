/**
 * docs/11-api-spec.md API-018~022 요청/응답 타입. SC-005~007이 공유하는 PENDING
 * 주문서(선점 타이머·픽업 시간대·결제)의 상태를 다룬다.
 *
 * 판단: API-018(선점 잔여 시간 조회)에는 품목·금액이 없다. SC-005·SC-007이 표시해야
 * 하는 "품목별 확정 단가·수량·합계"(06번)는 API-017(생성) 응답에만 담겨 있는데, 그
 * 응답은 장바구니 화면(이번 슬라이스 범위 밖)에서만 받고 버려진다. 새로고침에도
 * 견고해야 한다는 요구사항을 만족하려면 본인 주문을 상태 제한 없이 다시 조회할 수
 * 있는 API-024(주문 상세 조회, 11번 §5)가 필요해 이번 슬라이스에서 함께 쓴다 — 이미
 * 문서화된 엔드포인트를 호출하는 것이라 ARCHITECTURE.md가 금지하는 "새 아키텍처
 * 패턴"에 해당하지 않는다고 판단했다.
 */

export type OrderStatusValue =
  | "PENDING"
  | "CONFIRMED"
  | "READY"
  | "COMPLETED"
  | "CANCELED"
  | "NO_SHOW"
  | "EXPIRED"
  | "FAILED";

export interface OrderDetailItem {
  productId: number;
  name: string;
  quantity: number;
  unitPrice: number;
  lineAmount: number;
}

/** docs/11-api-spec.md API-024 응답의 `cancelUnavailableReason` 4종. */
export type CancelUnavailableReason =
  | "CANCEL_DEADLINE_PASSED"
  | "ALREADY_COMPLETED"
  | "ALREADY_CANCELED"
  | "NO_SHOW";

export type OrderActorType = "CUSTOMER" | "ADMIN" | "SYSTEM";

/** docs/11-api-spec.md API-024 응답 `statusHistory[]`. SC-010 "상태 변경 이력"에 쓴다. */
export interface OrderStatusHistoryEntry {
  toStatus: OrderStatusValue;
  actorType: OrderActorType;
  occurredAt: string;
}

/** docs/11-api-spec.md API-024 응답 `store`. SC-008·SC-010이 매장 요약에 쓴다. */
export interface OrderStoreSummary {
  name: string;
  address: string;
  phone: string;
}

/**
 * docs/11-api-spec.md API-024 응답. PENDING 주문도 본인 소유면 조회할 수 있다.
 * `store`·`statusHistory`는 SC-008(주문 완료)·SC-010(주문 상세)이 필요로 하는 매장 요약과
 * 상태 변경 이력이다(11번 응답 예시에 있으나 SC-005~007 슬라이스에서는 쓰지 않아 빠져
 * 있었다). `noShowAt`·`refunded`는 NO_SHOW 주문에만 내려온다(11번 §5 API-024 설명).
 */
export interface OrderDetailResponse {
  serverTime: string;
  orderId: number;
  orderNo: string;
  status: OrderStatusValue;
  orderedAt: string;
  items: OrderDetailItem[];
  totalAmount: number;
  pickupNumber: string | null;
  pickupStartAt: string | null;
  pickupEndAt: string | null;
  noShowDueAt: string | null;
  noShowAt?: string | null;
  refunded?: boolean;
  cancelable: boolean;
  cancelableUntil: string | null;
  cancelUnavailableReason: CancelUnavailableReason | null;
  canceledBy: OrderActorType | null;
  cancelReason: string | null;
  store: OrderStoreSummary;
  statusHistory: OrderStatusHistoryEntry[];
}

/** docs/11-api-spec.md API-023 요청 `status` 허용값(전체 조회는 생략). */
export type OrderListStatusFilter = "IN_PROGRESS" | "COMPLETED" | "CANCELED" | "NO_SHOW";

/** docs/11-api-spec.md API-023 응답 `items[]`. SC-009 주문 카드에 쓴다. */
export interface OrderListItem {
  orderId: number;
  orderNo: string;
  orderedAt: string;
  status: OrderStatusValue;
  pickupStartAt: string | null;
  pickupEndAt: string | null;
  pickupNumber: string | null;
  totalAmount: number;
  itemSummary: string;
}

/** docs/11-api-spec.md API-023 응답. */
export interface OrderListResponse {
  items: OrderListItem[];
  page: { number: number; size: number; totalElements: number };
}

/** docs/11-api-spec.md API-025 응답 `stockResults[]`. */
export interface CancelOrderStockResult {
  productId: number;
  quantity: number;
  restored: boolean;
  reason: "CANCEL_RESTORE" | "CANCEL_DISCARD";
  note?: string;
}

/** docs/11-api-spec.md API-025 응답. */
export interface CancelOrderResponse {
  orderId: number;
  status: OrderStatusValue;
  canceledAt: string;
  canceledBy: OrderActorType;
  slotReleased: boolean;
  stockResults: CancelOrderStockResult[];
}

/** docs/11-api-spec.md API-018 응답. */
export interface HoldStatusResponse {
  orderId: number;
  status: OrderStatusValue;
  serverTime: string;
  holdExpiresAt: string;
  holdRemainingSeconds: number;
  expiringSoon: boolean;
  paymentAttemptRemaining: number;
}

/** docs/11-api-spec.md API-019 응답. */
export interface AbandonOrderResponse {
  orderId: number;
  status: OrderStatusValue;
  releasedAt: string;
}

/** docs/11-api-spec.md API-020: 시간대·날짜가 선택 불가한 이유 5종. */
export type SlotUnselectableReason =
  | "RESERVATION_CLOSED"
  | "SLOT_FULL"
  | "AFTER_PRODUCT_CLOSING"
  | "BLOCKED"
  | "HOLIDAY";

export interface SelectableDate {
  date: string;
  label: string;
  selectable: boolean;
  unselectableReason?: SlotUnselectableReason;
}

export interface PickupSlot {
  slotId: number;
  date: string;
  startAt: string;
  endAt: string;
  capacity: number;
  reservedCount: number;
  selectable: boolean;
  unselectableReason?: SlotUnselectableReason;
}

/** docs/11-api-spec.md API-020 응답. */
export interface PickupSlotsResponse {
  serverTime: string;
  selectableDates: SelectableDate[];
  slots: PickupSlot[];
}

/** docs/11-api-spec.md API-021 응답. */
export interface AssignPickupSlotResponse {
  orderId: number;
  pickupSlotId: number;
  pickupStartAt: string;
  pickupEndAt: string;
  holdRemainingSeconds: number;
}

/** docs/11-api-spec.md API-022 응답 — 성공(`result: "SUCCEEDED"`). */
export interface PaymentSucceededResponse {
  result: "SUCCEEDED";
  orderId: number;
  orderNo: string;
  status: OrderStatusValue;
  pickupNumber: string;
  pickupBusinessDate: string;
  pickupStartAt: string;
  pickupEndAt: string;
  paidAmount: number;
  cancelableUntil: string;
  noShowDueAt: string;
  confirmedAt: string;
}

/**
 * docs/11-api-spec.md API-022 응답 — 실패(`result: "FAILED"`, HTTP 200).
 * `status`가 `FAILED`면 3회째(최종) 실패다. `holdExpiresAt`·`holdRemainingSeconds`는
 * 1~2회째 실패(선점 유지)에서만 내려온다.
 */
export interface PaymentFailedResponse {
  result: "FAILED";
  code: string;
  orderId: number;
  status: OrderStatusValue;
  attemptNo: number;
  paymentAttemptRemaining: number;
  holdExpiresAt?: string;
  holdRemainingSeconds?: number;
  holdReleased?: boolean;
  failureReason: string;
  message: string;
}

export type PaymentResponse = PaymentSucceededResponse | PaymentFailedResponse;
