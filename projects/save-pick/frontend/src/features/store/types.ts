/** docs/11-api-spec.md API-009 응답. 비로그인도 조회 가능한 매장 정보(SC-015). */
export interface StoreInfoResponse {
  name: string;
  address: string;
  phone: string;
  openTime: string;
  closeTime: string;
  slotUnitMinutes: number;
}

/** docs/11-api-spec.md API-120 응답. 관리자 전용 매장 운영 설정 조회(SC-113). */
export interface AdminStoreSettingsResponse {
  name: string;
  address: string;
  phone: string;
  openTime: string;
  closeTime: string;
  slotUnitMinutes: number;
  defaultSlotCapacity: number;
  holidays: string[];
}

/** docs/11-api-spec.md API-121 요청. */
export interface UpdateStoreSettingsRequest {
  openTime: string;
  closeTime: string;
  defaultSlotCapacity: number;
  holidays: string[];
}

/** docs/11-api-spec.md API-121 응답. */
export interface UpdateStoreSettingsResponse {
  openTime: string;
  closeTime: string;
  defaultSlotCapacity: number;
  holidays: string[];
  excludedFutureSlotCount: number;
  keptConfirmedOrderCount: number;
  appliedFrom: string;
}
