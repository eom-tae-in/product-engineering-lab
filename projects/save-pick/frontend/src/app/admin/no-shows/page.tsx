"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { fetchAdminOrders } from "@/features/admin-order/api";
import type { AdminOrderListItem } from "@/features/admin-order/types";
import { OrderStatusBadge } from "@/components/ui/OrderStatusBadge";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Skeleton } from "@/components/ui/Skeleton";
import { formatKstTime, formatWon } from "@/lib/format";
import { kstDateString, nowOnServer } from "@/lib/server-time";

type Tab = "NO_SHOW" | "UPCOMING";

const TABS: { value: Tab; label: string }[] = [
  { value: "NO_SHOW", label: "노쇼" },
  { value: "UPCOMING", label: "전환 예정" },
];

/** 유예 배지 기준(docs/06 SC-112 "전환 예정 배지 규칙"). 15분 이하이면 경고 배지를 추가한다. */
const GRACE_BADGE_THRESHOLD_MINUTES = 15;

function pickupRangeOf(item: AdminOrderListItem): string {
  if (!item.pickupStartAt || !item.pickupEndAt) return "-";
  return `${formatKstTime(item.pickupStartAt)}~${formatKstTime(item.pickupEndAt)}`;
}

function remainingGraceMinutes(noShowDueAt: string, nowMs: number): number {
  return Math.max(0, Math.ceil((new Date(noShowDueAt).getTime() - nowMs) / 60000));
}

/**
 * SC-112 · 노쇼 목록 (docs/06-screen-list.md §4).
 * 관리자 전용 화면이라 `app/admin/layout.tsx`의 AdminGate가 이미 인증을 보장한다.
 * Client Component에서 마운트 시 `authScope: "admin"`으로 직접 호출한다
 * (ARCHITECTURE.md 데이터 페칭 규칙). API-112를 `status=NO_SHOW`/`CONFIRMED`/`READY`로
 * 각각 불러 두 탭(노쇼·전환 예정)을 구성한다(11번 §9 API-112 설명, A-A6).
 *
 * 판단: docs/06 SC-112 상태표의 "오류(중복 처리)"는 관리자가 수동으로 "노쇼 처리"를
 * 시도하는 상황을 전제한다. 하지만 docs/11 §가정 A-A7은 "관리자 수동 노쇼 처리 API를
 * 만들지 않는다(자동 전환이 정본)"고 명시하고, §0.5 오류 코드 카탈로그에도 이 화면
 * 전용의 중복 처리 오류가 없다. 그래서 이 화면에는 수동 "노쇼 처리" 버튼과 그 오류
 * 시트를 만들지 않았다 — 06과 11이 어긋나는 지점이라 최종 보고에 남긴다.
 *
 * 판단: `AdminOrderListItem`(API-112 응답)에는 "노쇼 판정 시각"이 별도 필드로 없다.
 * 배치(BATCH-03)가 `no_show_due_at <= now()`가 되는 즉시 전환하므로 `noShowDueAt`을
 * 판정 시각으로 그대로 쓴다(실제 판정 이벤트 시각은 주문 상세의 상태 이력에만 있고
 * 목록 API로는 오지 않는다) — 최종 보고에도 남긴다.
 */
export default function AdminNoShowListPage() {
  const [activeTab, setActiveTab] = useState<Tab>("NO_SHOW");
  const [noShowItems, setNoShowItems] = useState<AdminOrderListItem[]>([]);
  const [upcomingItems, setUpcomingItems] = useState<AdminOrderListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // effect 안에서 호출해도 setState가 동기 실행되지 않도록 async/await 대신
  // .then()/.catch() 콜백 안에서만 setState한다(react-hooks/set-state-in-effect,
  // lib/auth/customer-auth.tsx의 refresh().then(...) 패턴과 동일).
  const runLoad = useCallback(() => {
    const today = kstDateString(0);
    return Promise.all([
      fetchAdminOrders({ pickupDate: today, status: "NO_SHOW" }),
      fetchAdminOrders({ pickupDate: today, status: "CONFIRMED" }),
      fetchAdminOrders({ pickupDate: today, status: "READY" }),
    ])
      .then(([noShow, confirmed, ready]) => {
        setNoShowItems(noShow.items);
        setUpcomingItems(
          [...confirmed.items, ...ready.items]
            .filter((item): item is AdminOrderListItem & { noShowDueAt: string } =>
              item.noShowDueAt !== null
            )
            .sort((a, b) => a.noShowDueAt.localeCompare(b.noShowDueAt))
        );
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
    runLoad();
  }, [runLoad]);

  useEffect(() => {
    runLoad();
  }, [runLoad]);

  const now = nowOnServer();

  return (
    <div className="flex flex-col gap-3 p-4">
      <h1 className="font-heading">노쇼</h1>

      <div className="flex gap-2">
        {TABS.map((tab) => (
          <button
            key={tab.value}
            type="button"
            onClick={() => setActiveTab(tab.value)}
            aria-pressed={activeTab === tab.value}
            className={`font-body h-11 flex-1 rounded-md border ${
              activeTab === tab.value
                ? "border-brand bg-brand text-on-brand"
                : "border-border text-text"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <p className="font-caption text-text-weak">
        노쇼는 픽업 종료 30분 뒤 자동으로 전환돼요. 재고는 복구되지 않아요
      </p>

      {loading ? (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
        </div>
      ) : loadError ? (
        <ErrorState message={loadError} onRetry={retryLoad} />
      ) : activeTab === "NO_SHOW" ? (
        noShowItems.length === 0 ? (
          <EmptyState
            message="오늘 노쇼로 처리된 주문이 없어요"
            action={
              <Button
                variant="secondary"
                className="w-auto px-6"
                onClick={() => setActiveTab("UPCOMING")}
              >
                전환 예정 보기
              </Button>
            }
          />
        ) : (
          <ul className="flex flex-col gap-2">
            {noShowItems.map((item) => (
              <li key={item.orderId}>
                <Link
                  href={`/admin/orders/${item.orderId}`}
                  className="block rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-heading tabular-nums text-brand">
                      {item.pickupNumber ?? "-"}
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
                    {`픽업 ${pickupRangeOf(item)} · 노쇼 판정 ${
                      item.noShowDueAt ? formatKstTime(item.noShowDueAt) : "-"
                    }`}
                  </p>
                  <p className="font-caption text-text-weak">{item.orderNo}</p>
                </Link>
              </li>
            ))}
          </ul>
        )
      ) : upcomingItems.length === 0 ? (
        // 판단: docs/06은 "전환 예정" 탭의 빈 상태 문구를 못박지 않는다. "노쇼" 탭 문구
        // 형식(사실 문구만)을 그대로 따라 최소한으로 채웠다 — 최종 보고에도 남긴다.
        <EmptyState message="전환 예정인 주문이 없어요" />
      ) : (
        <ul className="flex flex-col gap-2">
          {upcomingItems.map((item) => {
            const graceMinutes = remainingGraceMinutes(item.noShowDueAt as string, now);
            const showGraceBadge = graceMinutes <= GRACE_BADGE_THRESHOLD_MINUTES;
            return (
              <li key={item.orderId}>
                <Link
                  href={`/admin/orders/${item.orderId}`}
                  className="block rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-heading tabular-nums text-brand">
                      {item.pickupNumber ?? "-"}
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
                    {`픽업 ${pickupRangeOf(item)} · 전환 예정 ${formatKstTime(
                      item.noShowDueAt as string
                    )}`}
                    {showGraceBadge ? (
                      <span className="font-caption ml-2 inline-flex h-[22px] items-center rounded-sm bg-warning-weak px-2 text-warning">
                        {`유예 ${graceMinutes}분 남음`}
                      </span>
                    ) : null}
                  </p>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
