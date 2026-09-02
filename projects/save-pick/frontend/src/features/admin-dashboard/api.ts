import { fetchAdminOrders } from "@/features/admin-order/api";
import { fetchAdminStocks } from "@/features/stock/api";
import { fetchPickupSlots } from "@/features/pickup/api";
import { kstDateString, nowOnServer } from "@/lib/server-time";
import type { AdminHomeSummary } from "./types";

/**
 * SC-102 "오늘 요약" + "다음 픽업 시간대"를 조합해 만든다(`types.ts` 상단 주석 참고).
 * 오늘 픽업 기준 확정·준비 완료·픽업 완료·노쇼 건수는 API-112를 상태별로 4번 불러
 * `page.totalElements`만 쓴다 — 목록 API를 집계용으로 쓰는 것이지만, 06이 요구하는
 * 개수를 얻을 다른 방법이 없다.
 */
export function fetchAdminHomeSummary(): Promise<AdminHomeSummary> {
  const today = kstDateString(0);

  return Promise.all([
    fetchAdminOrders({ pickupDate: today, status: "CONFIRMED" }),
    fetchAdminOrders({ pickupDate: today, status: "READY" }),
    fetchAdminOrders({ pickupDate: today, status: "COMPLETED" }),
    fetchAdminOrders({ pickupDate: today, status: "NO_SHOW" }),
    fetchAdminStocks({ onlyUnavailable: true }),
    fetchPickupSlots(today),
  ]).then(([confirmed, ready, completed, noShow, stocks, pickupSlots]) => {
    const now = nowOnServer();
    const nextSlot =
      pickupSlots.slots.find((slot) => new Date(slot.startAt).getTime() > now) ?? null;

    return {
      confirmedCount: confirmed.page.totalElements,
      readyCount: ready.page.totalElements,
      completedCount: completed.page.totalElements,
      noShowCount: noShow.page.totalElements,
      unavailableProductCount: stocks.page.totalElements,
      nextSlot: nextSlot
        ? {
            slotId: nextSlot.slotId,
            startAt: nextSlot.startAt,
            endAt: nextSlot.endAt,
            reservedCount: nextSlot.reservedCount,
            capacity: nextSlot.capacity,
          }
        : null,
    };
  });
}
