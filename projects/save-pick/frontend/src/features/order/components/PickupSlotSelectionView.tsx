"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth/customer-auth";
import { assignPickupSlot, abandonOrder, fetchOrderHold, fetchPickupSlots } from "@/features/order/api";
import type { PickupSlot, PickupSlotsResponse, SlotUnselectableReason } from "@/features/order/types";
import { HoldExpiredSheet } from "./HoldExpiredSheet";
import { HoldTimerBar } from "@/components/ui/HoldTimerBar";
import { PickupSlotChip, type PickupSlotChipStatus } from "@/components/ui/PickupSlotChip";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { Skeleton } from "@/components/ui/Skeleton";
import { ApiError } from "@/lib/api-client";
import { formatKstTime } from "@/lib/format";
import { nowOnServer } from "@/lib/server-time";

interface SelectedSlot {
  slotId: number;
  startAt: string;
  endAt: string;
}

/** BR-018: 픽업 시작 1시간 전까지 취소할 수 있다. 서버가 준 시각의 절대 시점에서
 * 60분을 빼 KST 벽시계 값을 다시 뽑는다("+09:00으로 온 문자열을 그대로 자른다"는
 * 다른 화면들과 달리 여기는 시각 연산이 필요해 `Date`를 쓰되, 브라우저 로컬 타임존에
 * 기대지 않도록 UTC 게터로 KST를 직접 계산한다). */
function kstClockOneHourBefore(iso: string): string {
  const oneHourBeforeMs = new Date(iso).getTime() - 60 * 60 * 1000;
  const kst = new Date(oneHourBeforeMs + 9 * 60 * 60 * 1000);
  const hh = String(kst.getUTCHours()).padStart(2, "0");
  const mm = String(kst.getUTCMinutes()).padStart(2, "0");
  return `${hh}:${mm}`;
}

function hasSelectableSlot(slots: PickupSlot[], date: string | undefined): boolean {
  if (!date) return false;
  return slots.some((slot) => slot.date === date && slot.selectable);
}

function chipStatus(slot: PickupSlot, selectedSlotId: number | null): PickupSlotChipStatus {
  if (slot.slotId === selectedSlotId) return "selected";
  if (slot.selectable) return "selectable";
  const reason: SlotUnselectableReason | undefined = slot.unselectableReason;
  if (reason === "RESERVATION_CLOSED") return "reservationClosed";
  if (reason === "SLOT_FULL") return "full";
  if (reason === "AFTER_PRODUCT_CLOSING") return "afterProductClosing";
  // BLOCKED·HOLIDAY: docs/06·09 칩 상태 6종에 HOLIDAY 전용 칩이 없어 "운영 차단"으로
  // 묶는다. 휴무일은 보통 날짜 단위(selectableDates)로 걸러져 개별 슬롯까지 잘
  // 내려오지 않는다.
  return "blocked";
}

function chipSecondaryLabel(slot: PickupSlot): string {
  if (slot.unselectableReason === "SLOT_FULL") return `정원 ${slot.reservedCount}/${slot.capacity}`;
  if (slot.unselectableReason === "RESERVATION_CLOSED") return "마감";
  if (slot.unselectableReason === "AFTER_PRODUCT_CLOSING") return "상품 마감 이후";
  if (slot.unselectableReason === "BLOCKED" || slot.unselectableReason === "HOLIDAY") return "운영 중단";
  return `${slot.reservedCount}/${slot.capacity}`;
}

function reasonBuckets(slots: PickupSlot[]): string[] {
  const buckets = new Set<string>();
  for (const slot of slots) {
    if (slot.unselectableReason === "SLOT_FULL") buckets.add("정원 초과");
    else if (slot.unselectableReason === "AFTER_PRODUCT_CLOSING") buckets.add("상품 마감 시각 초과");
    else if (
      slot.unselectableReason === "RESERVATION_CLOSED" ||
      slot.unselectableReason === "BLOCKED" ||
      slot.unselectableReason === "HOLIDAY"
    ) {
      buckets.add("영업 종료");
    }
  }
  return Array.from(buckets);
}

type AssignSheetState = { message: string } | null;

/**
 * SC-006 · 픽업 날짜·시간대 선택 (docs/06-screen-list.md §3).
 * 로그인 필수. 마운트 시 선점 잔여 시간(API-018)과 선택 가능한 픽업 시간대
 * (API-020)를 다시 조회한다. `date` 파라미터 없이 한 번만 불러와 오늘·내일 탭
 * 전환은 클라이언트에서 필터링으로 처리한다.
 */
export function PickupSlotSelectionView() {
  const auth = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const orderIdParam = searchParams.get("orderId");
  const orderId = orderIdParam ? Number(orderIdParam) : NaN;

  const [slotsResp, setSlotsResp] = useState<PickupSlotsResponse | null>(null);
  const [holdExpiresAt, setHoldExpiresAt] = useState<string | null>(null);
  const [activeDate, setActiveDate] = useState<string | null>(null);
  const [selectedSlot, setSelectedSlot] = useState<SelectedSlot | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadErrorOpen, setLoadErrorOpen] = useState(false);
  const [expired, setExpired] = useState(false);
  const [assignSheet, setAssignSheet] = useState<AssignSheetState>(null);
  const [assigning, setAssigning] = useState<number | null>(null);
  const [abandoning, setAbandoning] = useState(false);

  const runLoad = useCallback(() => {
    return Promise.all([fetchOrderHold(orderId), fetchPickupSlots(orderId)])
      .then(([hold, slots]) => {
        if (hold.status === "EXPIRED") {
          setExpired(true);
          return;
        }
        setHoldExpiresAt(hold.holdExpiresAt);
        setSlotsResp(slots);
        setLoadErrorOpen(false);

        const today = slots.selectableDates.find((d) => d.label === "D+0");
        const tomorrow = slots.selectableDates.find((d) => d.label === "D+1");
        if (hasSelectableSlot(slots.slots, today?.date)) {
          setActiveDate((prev) => prev ?? today?.date ?? null);
        } else if (hasSelectableSlot(slots.slots, tomorrow?.date)) {
          setActiveDate(tomorrow?.date ?? null);
        } else {
          setActiveDate((prev) => prev ?? today?.date ?? null);
        }
      })
      .catch(() => setLoadErrorOpen(true))
      .finally(() => setLoading(false));
  }, [orderId]);

  useEffect(() => {
    if (auth.status === "guest") {
      router.replace("/login");
    }
  }, [auth.status, router]);

  useEffect(() => {
    if (auth.status === "authenticated" && Number.isFinite(orderId)) {
      void runLoad();
    } else if (auth.status === "authenticated") {
      router.replace("/cart");
    }
    // router는 next/navigation이 안정적인 참조를 보장하지 않는 mock 환경(테스트)에서
    // 매 렌더 재실행을 유발할 수 있어 의도적으로 의존성에서 뺀다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth.status, orderId, runLoad]);

  const today = slotsResp?.selectableDates.find((d) => d.label === "D+0");
  const tomorrow = slotsResp?.selectableDates.find((d) => d.label === "D+1");
  const todayHasSelectable = hasSelectableSlot(slotsResp?.slots ?? [], today?.date);
  const tomorrowHasSelectable = hasSelectableSlot(slotsResp?.slots ?? [], tomorrow?.date);
  const bothUnavailable = slotsResp !== null && !todayHasSelectable && !tomorrowHasSelectable;

  const activeSlots = useMemo(
    () => (slotsResp ? slotsResp.slots.filter((slot) => slot.date === activeDate) : []),
    [slotsResp, activeDate]
  );

  async function handleAssign(slot: PickupSlot) {
    setAssigning(slot.slotId);
    try {
      const result = await assignPickupSlot(orderId, slot.slotId);
      setSelectedSlot({
        slotId: result.pickupSlotId,
        startAt: result.pickupStartAt,
        endAt: result.pickupEndAt,
      });
      setHoldExpiresAt(
        new Date(nowOnServer() + result.holdRemainingSeconds * 1000).toISOString()
      );
    } catch (error) {
      if (error instanceof ApiError && error.code === "HOLD_EXPIRED") {
        setExpired(true);
      } else if (error instanceof ApiError && error.code === "SLOT_FULL") {
        setAssignSheet({ message: "방금 정원이 찼어요. 다른 시간대를 골라주세요" });
        void fetchPickupSlots(orderId).then(setSlotsResp);
      } else if (error instanceof ApiError) {
        setAssignSheet({ message: error.defaultMessage });
        void fetchPickupSlots(orderId).then(setSlotsResp);
      } else {
        setAssignSheet({
          message: "일시적인 오류로 처리하지 못했어요. 잠시 뒤 다시 시도해주세요.",
        });
      }
    } finally {
      setAssigning(null);
    }
  }

  async function handleAbandon() {
    setAbandoning(true);
    try {
      await abandonOrder(orderId);
    } catch {
      // 이미 유효하지 않은 주문서이거나 통신 오류여도 장바구니로 보낸다.
    } finally {
      setAbandoning(false);
      router.push("/cart");
    }
  }

  if (auth.status !== "authenticated" || loading) {
    return (
      <div className="flex flex-1 flex-col gap-3 p-4">
        <Skeleton className="h-11 w-full" />
        <Skeleton className="h-11 w-full" />
        <div className="grid grid-cols-3 gap-2">
          {Array.from({ length: 12 }, (_, index) => (
            <Skeleton key={index} className="h-14 w-[100px]" />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-1 flex-col">
      <HoldExpiredSheet open={expired} />

      {!expired && holdExpiresAt ? (
        <HoldTimerBar holdExpiresAt={holdExpiresAt} onExpire={() => setExpired(true)} />
      ) : null}

      {!expired && bothUnavailable ? (
        <div className="p-4">
          <EmptyState
            message="지금은 예약할 수 있는 픽업 시간대가 없어요"
            reason={reasonBuckets(slotsResp?.slots ?? []).join(" · ") || undefined}
            action={
              <Button
                variant="secondary"
                className="w-auto px-6"
                onClick={() => void handleAbandon()}
                disabled={abandoning}
              >
                주문서 포기하고 장바구니로
              </Button>
            }
          />
        </div>
      ) : null}

      {!expired && !bothUnavailable && slotsResp ? (
        <div className="flex flex-1 flex-col gap-4 p-4">
          <div className="flex gap-2">
            {today ? (
              <button
                type="button"
                onClick={() => setActiveDate(today.date)}
                className={`font-body h-12 flex-1 rounded-md border ${
                  activeDate === today.date
                    ? "border-brand bg-brand text-on-brand"
                    : "border-border bg-surface text-text"
                }`}
              >
                오늘
              </button>
            ) : null}
            {tomorrow ? (
              <button
                type="button"
                onClick={() => setActiveDate(tomorrow.date)}
                className={`font-body h-12 flex-1 rounded-md border ${
                  activeDate === tomorrow.date
                    ? "border-brand bg-brand text-on-brand"
                    : "border-border bg-surface text-text"
                }`}
              >
                내일
              </button>
            ) : null}
          </div>

          {activeDate === today?.date && !todayHasSelectable ? (
            <EmptyState message="오늘은 선택할 수 있는 시간대가 없어요" />
          ) : (
            <div className="grid grid-cols-3 gap-2">
              {activeSlots.map((slot) => (
                <PickupSlotChip
                  key={slot.slotId}
                  status={chipStatus(slot, selectedSlot?.slotId ?? null)}
                  timeLabel={`${formatKstTime(slot.startAt)}~${formatKstTime(slot.endAt)}`}
                  secondaryLabel={chipSecondaryLabel(slot)}
                  onSelect={
                    assigning !== null ? undefined : () => void handleAssign(slot)
                  }
                />
              ))}
            </div>
          )}

          {selectedSlot ? (
            <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
              <p className="font-body text-text">
                {`선택한 시간대: ${formatKstTime(selectedSlot.startAt)}~${formatKstTime(
                  selectedSlot.endAt
                )}`}
              </p>
              <p className="font-caption mt-1 text-text-weak">
                {`${kstClockOneHourBefore(selectedSlot.startAt)}까지 취소할 수 있어요`}
              </p>
            </div>
          ) : null}
        </div>
      ) : null}

      {!expired && !bothUnavailable && slotsResp ? (
        <div className="sticky bottom-0 flex items-center gap-3 border-t border-border bg-surface p-4">
          <Button
            className="w-auto px-6"
            disabled={!selectedSlot}
            onClick={() => router.push(`/orders/new/payment?orderId=${orderId}`)}
          >
            결제하기
          </Button>
        </div>
      ) : null}

      <BottomSheet open={loadErrorOpen} dismissible={slotsResp !== null}>
        <div className="flex flex-col gap-3">
          <p className="font-body text-text">시간대를 불러오지 못했어요</p>
          <Button
            onClick={() => {
              setLoading(true);
              void runLoad();
            }}
          >
            다시 시도
          </Button>
        </div>
      </BottomSheet>

      <BottomSheet open={assignSheet !== null} onClose={() => setAssignSheet(null)}>
        <div className="flex flex-col gap-3">
          <p className="font-body text-text">{assignSheet?.message}</p>
          <Button variant="secondary" onClick={() => setAssignSheet(null)}>
            닫기
          </Button>
        </div>
      </BottomSheet>
    </div>
  );
}
