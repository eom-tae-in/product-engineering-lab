/**
 * docs/11-api-spec.md API-118·119 요청/응답 타입. SC-102(다음 픽업 시간대 요약)·
 * SC-111(시간대별 픽업 현황)이 쓴다.
 */

/** docs/11-api-spec.md API-118 응답 `slots[].itemTotals[]`. */
export interface PickupSlotItemTotal {
  productId: number;
  name: string;
  quantity: number;
}

/** docs/11-api-spec.md API-118 응답 `slots[]` 원소. */
export interface PickupSlot {
  slotId: number;
  startAt: string;
  endAt: string;
  capacity: number;
  /** CONFIRMED·READY·COMPLETED만 포함한다. CANCELED·NO_SHOW는 제외한다(FR-055, BR-016). */
  reservedCount: number;
  full: boolean;
  blocked: boolean;
  reservationClosed: boolean;
  itemTotals: PickupSlotItemTotal[];
}

/** docs/11-api-spec.md API-118 응답. */
export interface PickupSlotListResponse {
  date: string;
  isHoliday: boolean;
  slots: PickupSlot[];
}

/** docs/11-api-spec.md API-119 요청. 차단만 바꿀 때는 `capacity`를 생략한다(SC-111). */
export interface UpdatePickupSlotRequest {
  capacity?: number;
  blocked?: boolean;
}

/** docs/11-api-spec.md API-119 응답. */
export interface UpdatePickupSlotResponse {
  slotId: number;
  capacity: number;
  reservedCount: number;
  blocked: boolean;
  overCapacity: boolean;
  keptOrderCount: number;
}
