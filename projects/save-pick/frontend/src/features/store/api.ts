import { clientRequest, serverGet } from "@/lib/api-client";
import type {
  AdminStoreSettingsResponse,
  StoreInfoResponse,
  UpdateStoreSettingsRequest,
  UpdateStoreSettingsResponse,
} from "./types";

/**
 * API-009 매장 정보 조회. 비로그인도 볼 수 있는 조회 전용 화면(SC-015)이라
 * Server Component에서 serverGet으로 직접 호출한다(ARCHITECTURE.md 데이터 페칭 규칙).
 */
export function fetchStoreInfo(): Promise<StoreInfoResponse> {
  return serverGet<StoreInfoResponse>("/api/store");
}

/** API-120 매장 운영 설정 조회. 관리자 전용(SC-113). */
export function fetchAdminStoreSettings(): Promise<AdminStoreSettingsResponse> {
  return clientRequest<AdminStoreSettingsResponse>("/api/admin/store-settings", {
    authScope: "admin",
  });
}

/** API-121 매장 운영 설정 변경. 관리자 전용(SC-113). */
export function updateAdminStoreSettings(
  request: UpdateStoreSettingsRequest
): Promise<UpdateStoreSettingsResponse> {
  return clientRequest<UpdateStoreSettingsResponse>("/api/admin/store-settings", {
    method: "PUT",
    authScope: "admin",
    body: request,
  });
}
