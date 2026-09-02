import { clientRequest } from "@/lib/api-client";
import type {
  PickupSlotListResponse,
  UpdatePickupSlotRequest,
  UpdatePickupSlotResponse,
} from "./types";

/** API-118 시간대별 픽업 현황 조회. SC-102(다음 픽업 시간대 요약)·SC-111이 쓴다. */
export function fetchPickupSlots(date: string): Promise<PickupSlotListResponse> {
  const query = new URLSearchParams({ date });
  return clientRequest<PickupSlotListResponse>(
    `/api/admin/pickup-slots?${query.toString()}`,
    { method: "GET", authScope: "admin" }
  );
}

/** API-119 개별 시간대 정원 변경·차단. SC-111의 차단·해제 액션이 쓴다. */
export function updatePickupSlot(
  slotId: number,
  request: UpdatePickupSlotRequest
): Promise<UpdatePickupSlotResponse> {
  return clientRequest<UpdatePickupSlotResponse>(`/api/admin/pickup-slots/${slotId}`, {
    method: "PATCH",
    authScope: "admin",
    body: request,
  });
}
