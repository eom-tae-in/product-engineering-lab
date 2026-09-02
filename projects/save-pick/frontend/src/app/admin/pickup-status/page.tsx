"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { fetchAdminOrders, markOrderReady } from "@/features/admin-order/api";
import type { AdminOrderListItem } from "@/features/admin-order/types";
import { fetchPickupSlots, updatePickupSlot } from "@/features/pickup/api";
import type { PickupSlot, PickupSlotListResponse } from "@/features/pickup/types";
import { OrderStatusBadge } from "@/components/ui/OrderStatusBadge";
import { Button } from "@/components/ui/Button";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Skeleton } from "@/components/ui/Skeleton";
import { ApiError } from "@/lib/api-client";
import { formatKstTime } from "@/lib/format";
import { kstDateString, nowOnServer } from "@/lib/server-time";

type DateFilter = "TODAY" | "TOMORROW";

const DATE_FILTERS: { value: DateFilter; label: string }[] = [
  { value: "TODAY", label: "오늘" },
  { value: "TOMORROW", label: "내일" },
];

function dateOf(filter: DateFilter): string {
  return filter === "TODAY" ? kstDateString(0) : kstDateString(1);
}

/** `YYYY-MM-DD` → `M/D` 표기(빈 상태 날짜 전환 버튼용). `Date` 파싱 없이 문자열만 자른다. */
function monthDayOf(dateString: string): string {
  const [, month, day] = dateString.split("-");
  return `${Number(month)}/${Number(day)}`;
}

function timeRangeOf(slot: PickupSlot): string {
  return `${formatKstTime(slot.startAt)}~${formatKstTime(slot.endAt)}`;
}

function itemQuantityOf(slot: PickupSlot): number {
  return slot.itemTotals.reduce((sum, item) => sum + item.quantity, 0);
}

type SlotState = "PAST" | "BLOCKED" | "FULL" | "OPEN";

function stateOf(slot: PickupSlot, nowMs: number): SlotState {
  if (slot.blocked) return "BLOCKED";
  if (new Date(slot.endAt).getTime() <= nowMs) return "PAST";
  if (slot.full || slot.reservedCount >= slot.capacity) return "FULL";
  return "OPEN";
}

/**
 * SC-111 · 시간대별 픽업 현황 (docs/06-screen-list.md §4).
 * 관리자 전용 화면이라 `app/admin/layout.tsx`의 AdminGate가 이미 인증을 보장한다.
 * Client Component에서 마운트 시 `authScope: "admin"`으로 직접 호출한다
 * (ARCHITECTURE.md 데이터 페칭 규칙).
 */
export default function AdminPickupStatusPage() {
  const [dateFilter, setDateFilter] = useState<DateFilter>("TODAY");
  const [data, setData] = useState<PickupSlotListResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [expandedSlotId, setExpandedSlotId] = useState<number | null>(null);
  const [expandedOrders, setExpandedOrders] = useState<AdminOrderListItem[] | null>(null);
  const [expandedLoading, setExpandedLoading] = useState(false);
  const [expandedError, setExpandedError] = useState<string | null>(null);
  const [bulkReadySubmitting, setBulkReadySubmitting] = useState(false);

  const [blockConfirmSlot, setBlockConfirmSlot] = useState<PickupSlot | null>(null);
  const [blockSubmitting, setBlockSubmitting] = useState(false);
  const [actionErrorSheet, setActionErrorSheet] = useState<string | null>(null);

  // effect 안에서 호출해도 setState가 동기 실행되지 않도록 async/await 대신
  // .then()/.catch() 콜백 안에서만 setState한다(react-hooks/set-state-in-effect,
  // lib/auth/customer-auth.tsx의 refresh().then(...) 패턴과 동일).
  const runLoad = useCallback((filter: DateFilter) => {
    return fetchPickupSlots(dateOf(filter))
      .then((response) => {
        setData(response);
        setLoadError(null);
      })
      .catch(() => {
        setLoadError("불러오지 못했어요");
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  const retryLoad = useCallback(() => {
    setLoading(true);
    setLoadError(null);
    runLoad(dateFilter);
  }, [runLoad, dateFilter]);

  // 날짜 필터 변경도 이 effect가 그대로 재조회한다(AdminOrderListPage와 동일 패턴).
  // 펼침 상태 초기화는 아래 handleDateFilterChange(이벤트 핸들러)에서 동기로 처리한다
  // — effect 본문에서 setState를 직접 호출하지 않기 위해서다(react-hooks/set-state-in-effect).
  useEffect(() => {
    runLoad(dateFilter);
  }, [dateFilter, runLoad]);

  function handleDateFilterChange(next: DateFilter) {
    setDateFilter(next);
    setExpandedSlotId(null);
    setExpandedOrders(null);
    setExpandedError(null);
  }

  function toggleExpand(slot: PickupSlot) {
    if (expandedSlotId === slot.slotId) {
      setExpandedSlotId(null);
      setExpandedOrders(null);
      setExpandedError(null);
      return;
    }
    setExpandedSlotId(slot.slotId);
    setExpandedOrders(null);
    setExpandedError(null);
    setExpandedLoading(true);
    fetchAdminOrders({ pickupDate: dateOf(dateFilter), slotId: slot.slotId })
      .then((response) => setExpandedOrders(response.items))
      .catch(() => setExpandedError("불러오지 못했어요"))
      .finally(() => setExpandedLoading(false));
  }

  async function handleBulkReady() {
    if (!expandedOrders || expandedSlotId === null) return;
    const confirmedOrders = expandedOrders.filter((order) => order.status === "CONFIRMED");
    if (confirmedOrders.length === 0) return;

    setBulkReadySubmitting(true);
    const results = await Promise.allSettled(
      confirmedOrders.map((order) => markOrderReady(order.orderId))
    );
    const allFailed = results.every((result) => result.status === "rejected");
    if (allFailed) {
      const [firstRejected] = results as PromiseRejectedResult[];
      const reason = firstRejected.reason;
      setActionErrorSheet(reason instanceof ApiError ? reason.defaultMessage : "처리하지 못했어요");
    }
    try {
      const refreshed = await fetchAdminOrders({
        pickupDate: dateOf(dateFilter),
        slotId: expandedSlotId,
      });
      setExpandedOrders(refreshed.items);
    } catch {
      // 새로고침 실패는 무시한다 — 직전 목록이 그대로 남는다.
    }
    setBulkReadySubmitting(false);
  }

  function openBlockConfirm(slot: PickupSlot) {
    setBlockConfirmSlot(slot);
  }

  function closeBlockConfirm() {
    setBlockConfirmSlot(null);
  }

  function applySlotUpdate(slotId: number, patch: Partial<PickupSlot>) {
    setData((prev) =>
      prev
        ? {
            ...prev,
            slots: prev.slots.map((slot) =>
              slot.slotId === slotId ? { ...slot, ...patch } : slot
            ),
          }
        : prev
    );
  }

  async function confirmBlock() {
    if (!blockConfirmSlot) return;
    setBlockSubmitting(true);
    try {
      const response = await updatePickupSlot(blockConfirmSlot.slotId, { blocked: true });
      applySlotUpdate(response.slotId, {
        blocked: response.blocked,
        capacity: response.capacity,
        reservedCount: response.reservedCount,
      });
      setBlockConfirmSlot(null);
    } catch (error) {
      setBlockConfirmSlot(null);
      setActionErrorSheet(error instanceof ApiError ? error.defaultMessage : "처리하지 못했어요");
    } finally {
      setBlockSubmitting(false);
    }
  }

  async function handleUnblock(slot: PickupSlot) {
    setActionErrorSheet(null);
    try {
      const response = await updatePickupSlot(slot.slotId, { blocked: false });
      applySlotUpdate(response.slotId, {
        blocked: response.blocked,
        capacity: response.capacity,
        reservedCount: response.reservedCount,
      });
    } catch (error) {
      setActionErrorSheet(error instanceof ApiError ? error.defaultMessage : "처리하지 못했어요");
    }
  }

  const now = nowOnServer();
  const isEmpty = data ? data.slots.every((slot) => slot.reservedCount === 0) : false;
  const otherDateFilter: DateFilter = dateFilter === "TODAY" ? "TOMORROW" : "TODAY";

  return (
    <div className="flex flex-col gap-3 p-4">
      <h1 className="font-heading">픽업 현황</h1>

      <div className="flex gap-2">
        {DATE_FILTERS.map((filter) => (
          <button
            key={filter.value}
            type="button"
            onClick={() => handleDateFilterChange(filter.value)}
            aria-pressed={dateFilter === filter.value}
            className={`font-body h-11 flex-1 rounded-md border ${
              dateFilter === filter.value
                ? "border-brand bg-brand text-on-brand"
                : "border-border text-text"
            }`}
          >
            {`${filter.label} ${monthDayOf(dateOf(filter.value))}`}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="flex flex-col gap-2">
          {Array.from({ length: 8 }).map((_, index) => (
            <Skeleton key={index} className="h-16 w-full" />
          ))}
        </div>
      ) : loadError ? (
        <ErrorState message={loadError} onRetry={retryLoad} />
      ) : isEmpty ? (
        <EmptyState
          message="이 날짜에 예약된 픽업이 없어요"
          action={
            <Button
              variant="secondary"
              className="w-auto px-6"
              onClick={() => handleDateFilterChange(otherDateFilter)}
            >
              {`${DATE_FILTERS.find((f) => f.value === otherDateFilter)?.label} ${monthDayOf(
                dateOf(otherDateFilter)
              )} 보기`}
            </Button>
          }
        />
      ) : data ? (
        <ul className="flex flex-col gap-2">
          {data.slots.map((slot) => {
            const state = stateOf(slot, now);
            const expanded = expandedSlotId === slot.slotId;
            return (
              <li
                key={slot.slotId}
                className={`rounded-lg border border-border bg-surface p-3 shadow-[var(--shadow-card)] ${
                  state === "PAST" || state === "BLOCKED" ? "opacity-50" : ""
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-heading tabular-nums">{timeRangeOf(slot)}</span>
                  {state === "PAST" ? (
                    <span className="font-caption inline-flex h-[22px] items-center rounded-sm bg-border px-2 text-text-weak">
                      종료
                    </span>
                  ) : state === "BLOCKED" ? (
                    <span className="font-caption inline-flex h-[22px] items-center rounded-sm bg-border px-2 text-text-weak">
                      운영 중단
                    </span>
                  ) : state === "FULL" ? (
                    <span className="font-caption inline-flex h-[22px] items-center rounded-sm bg-warning-weak px-2 text-warning">
                      {`${slot.reservedCount}/${slot.capacity} 정원 마감`}
                    </span>
                  ) : null}
                </div>
                <div className="mt-1 flex items-center justify-between gap-2">
                  <span className="font-caption tabular-nums text-text-weak">{`${slot.reservedCount}/${slot.capacity}건`}</span>
                  <span className="font-caption tabular-nums text-text-weak">{`품목 ${itemQuantityOf(
                    slot
                  )}개`}</span>
                </div>

                {state !== "PAST" ? (
                  <div className="mt-2 flex gap-2">
                    <button
                      type="button"
                      onClick={() => toggleExpand(slot)}
                      className="font-caption h-11 flex-1 rounded-md border border-border text-text"
                    >
                      {expanded ? "접기" : "펼치기"}
                    </button>
                    {state === "BLOCKED" ? (
                      <button
                        type="button"
                        onClick={() => void handleUnblock(slot)}
                        className="font-caption h-11 flex-1 rounded-md border border-border text-text"
                      >
                        해제
                      </button>
                    ) : (
                      <button
                        type="button"
                        onClick={() => openBlockConfirm(slot)}
                        className="font-caption h-11 flex-1 rounded-md border border-border text-text"
                      >
                        차단
                      </button>
                    )}
                  </div>
                ) : null}

                {expanded ? (
                  <div className="mt-2 border-t border-border pt-2">
                    {expandedLoading ? (
                      <div className="flex flex-col gap-2">
                        <Skeleton className="h-10 w-full" />
                        <Skeleton className="h-10 w-full" />
                      </div>
                    ) : expandedError ? (
                      <ErrorState message={expandedError} onRetry={() => toggleExpand(slot)} />
                    ) : expandedOrders && expandedOrders.length === 0 ? (
                      <p className="font-caption text-text-weak">예약된 주문이 없어요</p>
                    ) : expandedOrders ? (
                      <>
                        <ul className="flex flex-col gap-2">
                          {expandedOrders.map((order) => (
                            <li key={order.orderId}>
                              <Link
                                href={`/admin/orders/${order.orderId}`}
                                className="flex items-center justify-between gap-2"
                              >
                                <span className="font-body tabular-nums">
                                  {`${order.pickupNumber ?? "-"} ${order.customerName}`}
                                </span>
                                <OrderStatusBadge status={order.status} audience="admin" />
                              </Link>
                            </li>
                          ))}
                        </ul>
                        {expandedOrders.some((order) => order.status === "CONFIRMED") ? (
                          <Button
                            variant="secondary"
                            className="mt-2"
                            onClick={() => void handleBulkReady()}
                            disabled={bulkReadySubmitting}
                          >
                            {bulkReadySubmitting ? "처리하는 중이에요" : "확정 주문 일괄 준비 완료"}
                          </Button>
                        ) : null}
                        <p className="font-caption mt-1 text-text-weak">
                          취소·노쇼 주문은 예약 건수에 넣지 않아요
                        </p>
                      </>
                    ) : null}
                  </div>
                ) : null}
              </li>
            );
          })}
        </ul>
      ) : null}

      {/*
       * docs/06 SC-111은 차단 확인 시트를 별도 상태로 못박지 않는다. 차단은 고객이
       * 시간대를 새로 고를 수 없게 만드는 조작이라 `wireframes/sc-111-pickup-slot-status.html`의
       * 확인 시트 문구를 그대로 채택했다 — 최종 보고에도 남긴다.
       */}
      <BottomSheet open={blockConfirmSlot !== null} onClose={closeBlockConfirm}>
        {blockConfirmSlot ? (
          <div className="flex flex-col gap-3">
            <h3 className="font-heading">{`${timeRangeOf(blockConfirmSlot)} 시간대를 차단할까요?`}</h3>
            <p className="font-caption text-text-weak">
              차단하면 고객이 이 시간대를 새로 고를 수 없어요. 이미 확정된 주문은 취소되지 않아요.
            </p>
            <Button onClick={() => void confirmBlock()} disabled={blockSubmitting}>
              {blockSubmitting ? "처리하는 중이에요" : "차단하기"}
            </Button>
            <Button variant="secondary" onClick={closeBlockConfirm} disabled={blockSubmitting}>
              닫기
            </Button>
          </div>
        ) : null}
      </BottomSheet>

      <BottomSheet open={actionErrorSheet !== null} onClose={() => setActionErrorSheet(null)}>
        {actionErrorSheet ? (
          <div className="flex flex-col gap-3">
            <p className="font-body text-text">{actionErrorSheet}</p>
            <Button onClick={() => setActionErrorSheet(null)}>닫기</Button>
          </div>
        ) : null}
      </BottomSheet>
    </div>
  );
}
