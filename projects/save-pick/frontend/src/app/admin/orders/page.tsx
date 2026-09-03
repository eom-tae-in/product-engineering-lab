"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { fetchAdminOrders } from "@/features/admin-order/api";
import type { AdminOrderListItem, AdminOrderStatusFilter } from "@/features/admin-order/types";
import { OrderStatusBadge } from "@/components/ui/OrderStatusBadge";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Skeleton } from "@/components/ui/Skeleton";
import { formatKstDateTime, formatKstTime, formatWon } from "@/lib/format";
import { kstDateString } from "@/lib/server-time";
import { fetchPickupSlots } from "@/features/pickup/api";
import type { PickupSlot } from "@/features/pickup/types";

type DateFilter = "ALL" | "TODAY" | "TOMORROW";

interface DateFilterOption {
  value: DateFilter;
  label: string;
}

/** docs/06-screen-list.md SC-108 "필터: 픽업 날짜(오늘·내일 기본)" — 기본값은 두 날짜를 함께 보여준다. */
const DATE_FILTERS: DateFilterOption[] = [
  { value: "ALL", label: "오늘·내일" },
  { value: "TODAY", label: "오늘" },
  { value: "TOMORROW", label: "내일" },
];

interface StatusFilterOption {
  value: AdminOrderStatusFilter | "ALL";
  label: string;
}

/**
 * docs/11-api-spec.md API-112 `status` 값 중 이 화면이 필터로 노출하는 것.
 * PENDING·EXPIRED는 `wireframes/sc-108-admin-order-list.html`의 상태 select에도 없어
 * 목록에 두지 않는다(`features/admin-order/types.ts` 주석).
 */
const STATUS_FILTERS: StatusFilterOption[] = [
  { value: "ALL", label: "상태 전체" },
  { value: "CONFIRMED", label: "확정" },
  { value: "READY", label: "준비 완료" },
  { value: "COMPLETED", label: "픽업 완료" },
  { value: "CANCELED", label: "취소" },
  { value: "NO_SHOW", label: "노쇼" },
  { value: "FAILED", label: "결제 실패" },
];

function pickupDateOf(filter: DateFilter): string | undefined {
  if (filter === "TODAY") return kstDateString(0);
  if (filter === "TOMORROW") return kstDateString(1);
  return undefined;
}

function pickupTimeText(item: AdminOrderListItem): string | null {
  if (!item.pickupStartAt || !item.pickupEndAt) return null;
  return `${formatKstDateTime(item.pickupStartAt)}~${formatKstTime(item.pickupEndAt)}`;
}

/**
 * SC-108 · 주문 목록 (관리자) (docs/06-screen-list.md §4).
 * 관리자 전용 화면이라 `app/admin/layout.tsx`의 AdminGate가 인증을 이미 보장한다.
 * Client Component에서 마운트 시 `authScope: "admin"`으로 직접 호출한다
 * (ARCHITECTURE.md 데이터 페칭 규칙).
 *
 * 시간대 필터(06 SC-108 "주요 액션")는 날짜를 특정했을 때만 노출한다 — 시간대는 날짜에
 * 딸린 값이라 "오늘·내일"을 함께 보는 동안에는 고를 수 없다. 목록은 API-118(SC-111과
 * 공유)로 가져오고, 고른 시간대의 `slotId`를 API-112에 그대로 넘긴다.
 */
export default function AdminOrderListPage() {
  const [dateFilter, setDateFilter] = useState<DateFilter>("ALL");
  const [statusFilter, setStatusFilter] = useState<AdminOrderStatusFilter | "ALL">("ALL");
  const [slotFilter, setSlotFilter] = useState<number | "ALL">("ALL");
  const [slots, setSlots] = useState<PickupSlot[]>([]);
  const [items, setItems] = useState<AdminOrderListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // effect 안에서 호출해도 setState가 동기 실행되지 않도록 async/await 대신
  // .then()/.catch() 콜백 안에서만 setState한다(react-hooks/set-state-in-effect,
  // lib/auth/customer-auth.tsx의 refresh().then(...) 패턴과 동일).
  const runLoad = useCallback(
    (date: DateFilter, status: AdminOrderStatusFilter | "ALL", slot: number | "ALL") => {
    return fetchAdminOrders({
      pickupDate: pickupDateOf(date),
      status: status === "ALL" ? undefined : status,
      slotId: slot === "ALL" ? undefined : slot,
    })
      .then((response) => {
        setItems(response.items);
        setLoadError(null);
      })
      .catch(() => {
        setLoadError("불러오지 못했어요");
      })
      .finally(() => {
        setLoading(false);
      });
    },
    []
  );

  // 시간대 목록은 날짜를 특정했을 때만 의미가 있다(API-118은 날짜 단위 조회).
  // 실패해도 화면을 막지 않는다 — 시간대 필터만 못 쓰고 날짜·상태 필터는 그대로 쓴다.
  const runLoadSlots = useCallback((date: DateFilter) => {
    const pickupDate = pickupDateOf(date);
    if (!pickupDate) {
      return Promise.resolve().then(() => setSlots([]));
    }
    return fetchPickupSlots(pickupDate)
      .then((response) => setSlots(response.slots))
      .catch(() => setSlots([]));
  }, []);

  // 재시도(버튼 클릭)에서만 쓴다. 로딩 화면으로 되돌린 뒤 다시 불러온다.
  const retryLoad = useCallback(() => {
    setLoading(true);
    setLoadError(null);
    runLoad(dateFilter, statusFilter, slotFilter);
  }, [runLoad, dateFilter, statusFilter, slotFilter]);

  // 필터 변경도 이 effect가 그대로 재조회하므로 필터 버튼 클릭은 상태만 바꾼다
  // (AdminProductListPage·AdminStockListPage와 동일 패턴, react-hooks/set-state-in-effect).
  useEffect(() => {
    runLoad(dateFilter, statusFilter, slotFilter);
  }, [dateFilter, statusFilter, slotFilter, runLoad]);

  useEffect(() => {
    void runLoadSlots(dateFilter);
  }, [dateFilter, runLoadSlots]);

  function changeDateFilter(next: DateFilter) {
    setDateFilter(next);
    // 시간대는 날짜에 딸린 값이라 날짜가 바뀌면 이전 선택을 유지할 수 없다.
    setSlotFilter("ALL");
  }

  function resetFilters() {
    setDateFilter("ALL");
    setStatusFilter("ALL");
    setSlotFilter("ALL");
  }

  const appliedFilterLabels = [
    DATE_FILTERS.find((f) => f.value === dateFilter)?.label,
    statusFilter !== "ALL" ? STATUS_FILTERS.find((f) => f.value === statusFilter)?.label : null,
    slotFilter !== "ALL"
      ? (() => {
          const selected = slots.find((slot) => slot.slotId === slotFilter);
          return selected ? formatKstTime(selected.startAt) : null;
        })()
      : null,
  ].filter((label): label is string => Boolean(label));

  return (
    <div className="flex flex-col gap-3 p-4">
      <h1 className="font-heading">주문 관리</h1>

      <div className="flex flex-wrap gap-2">
        {DATE_FILTERS.map((filter) => (
          <button
            key={filter.value}
            type="button"
            onClick={() => changeDateFilter(filter.value)}
            aria-pressed={dateFilter === filter.value}
            className={`font-caption h-9 rounded-md border px-3 ${
              dateFilter === filter.value
                ? "border-brand bg-brand-weak text-brand"
                : "border-border text-text-weak"
            }`}
          >
            {filter.label}
          </button>
        ))}
      </div>

      <div className="flex gap-2 overflow-x-auto pb-1">
        {STATUS_FILTERS.map((filter) => (
          <button
            key={filter.value}
            type="button"
            onClick={() => setStatusFilter(filter.value)}
            aria-pressed={statusFilter === filter.value}
            className={`font-caption h-9 flex-none rounded-md border px-3 ${
              statusFilter === filter.value
                ? "border-brand bg-brand-weak text-brand"
                : "border-border text-text-weak"
            }`}
          >
            {filter.label}
          </button>
        ))}
      </div>

      {/* 시간대는 날짜에 딸린 값이라 날짜를 특정했을 때만 고를 수 있다(06 SC-108 주요 액션). */}
      {slots.length > 0 ? (
        <div className="flex gap-2 overflow-x-auto pb-1">
          <button
            type="button"
            onClick={() => setSlotFilter("ALL")}
            aria-pressed={slotFilter === "ALL"}
            className={`font-caption h-9 flex-none rounded-md border px-3 ${
              slotFilter === "ALL"
                ? "border-brand bg-brand-weak text-brand"
                : "border-border text-text-weak"
            }`}
          >
            시간대 전체
          </button>
          {slots.map((slot) => (
            <button
              key={slot.slotId}
              type="button"
              onClick={() => setSlotFilter(slot.slotId)}
              aria-pressed={slotFilter === slot.slotId}
              className={`font-caption h-9 flex-none rounded-md border px-3 tabular-nums ${
                slotFilter === slot.slotId
                  ? "border-brand bg-brand-weak text-brand"
                  : "border-border text-text-weak"
              }`}
            >
              {formatKstTime(slot.startAt)}
            </button>
          ))}
        </div>
      ) : null}

      {loading ? (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
        </div>
      ) : loadError ? (
        <ErrorState message={loadError} onRetry={retryLoad} />
      ) : items.length === 0 ? (
        <EmptyState
          message="조건에 맞는 주문이 없어요"
          reason={
            appliedFilterLabels.length > 0
              ? `적용된 필터: ${appliedFilterLabels.join(" · ")}`
              : undefined
          }
          action={
            <button
              type="button"
              onClick={resetFilters}
              className="font-body flex h-[52px] w-auto items-center justify-center rounded-md bg-brand px-6 font-medium text-on-brand"
            >
              필터 초기화
            </button>
          }
        />
      ) : (
        <ul className="flex flex-col gap-3">
          {items.map((item) => (
            <li key={item.orderId}>
              <Link
                href={`/admin/orders/${item.orderId}`}
                className="block rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-heading tabular-nums text-brand">
                    {item.pickupNumber ?? "픽업 번호 없음"}
                  </span>
                  <OrderStatusBadge status={item.status} audience="admin" />
                </div>
                <div className="mt-1 flex items-center justify-between gap-2">
                  <span className="font-body text-text">{item.customerName}</span>
                  <span className="font-body font-medium tabular-nums">
                    {formatWon(item.totalAmount)}
                  </span>
                </div>
                <p className="font-caption text-text-weak">
                  {[pickupTimeText(item), item.orderNo].filter(Boolean).join(" · ")}
                </p>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
