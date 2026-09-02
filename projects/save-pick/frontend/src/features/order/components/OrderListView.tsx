"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/customer-auth";
import { fetchOrders } from "../api";
import type { OrderListItem, OrderListStatusFilter } from "../types";
import { OrderStatusBadge } from "@/components/ui/OrderStatusBadge";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Skeleton } from "@/components/ui/Skeleton";
import { formatKstDateTime, formatKstTime, formatWon } from "@/lib/format";

interface FilterOption {
  value: OrderListStatusFilter | "ALL";
  label: string;
}

/** docs/06-screen-list.md SC-009 "주요 액션" 상태 필터 5종, API-023 `status` 값 그대로. */
const FILTERS: FilterOption[] = [
  { value: "ALL", label: "전체" },
  { value: "IN_PROGRESS", label: "진행 중" },
  { value: "COMPLETED", label: "완료" },
  { value: "CANCELED", label: "취소" },
  { value: "NO_SHOW", label: "노쇼" },
];

/**
 * SC-009 · 주문 내역 (docs/06-screen-list.md §3).
 * 로그인 필수, 본인 주문만 조회된다(FR-027). 액세스 토큰이 클라이언트에만 있어
 * Client Component에서 마운트 시 직접 호출한다(ARCHITECTURE.md 데이터 페칭 규칙).
 * `includeExpired`는 항상 생략한다 — SC-009 필터 5종에 EXPIRED가 없고, 기본 목록에도
 * 표시하지 않는다(06 SC-009 상태 배지 표기표, TC-056).
 */
export function OrderListView() {
  const auth = useAuth();
  const router = useRouter();

  const [filter, setFilter] = useState<OrderListStatusFilter | "ALL">("ALL");
  const [items, setItems] = useState<OrderListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const runLoad = useCallback((nextFilter: OrderListStatusFilter | "ALL") => {
    return fetchOrders(nextFilter === "ALL" ? {} : { status: nextFilter })
      .then((data) => {
        setItems(data.items);
        setLoadError(null);
      })
      .catch(() => {
        setLoadError("주문 내역을 불러오지 못했어요");
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  useEffect(() => {
    if (auth.status === "guest") {
      router.replace("/login");
    }
  }, [auth.status, router]);

  // 최초 진입 시 한 번만 "전체" 목록을 불러온다. 필터 변경은 이벤트 핸들러
  // (handleFilterChange)가 직접 처리한다 — effect 안에서 setState를 동기 실행하지
  // 않기 위해서다(react-hooks/set-state-in-effect, me/page.tsx의 loadData와 동일 패턴).
  useEffect(() => {
    if (auth.status === "authenticated") {
      void runLoad("ALL");
    }
  }, [auth.status, runLoad]);

  function handleFilterChange(next: OrderListStatusFilter | "ALL") {
    setFilter(next);
    setLoading(true);
    setLoadError(null);
    void runLoad(next);
  }

  function retryLoad() {
    setLoading(true);
    setLoadError(null);
    void runLoad(filter);
  }

  if (auth.status !== "authenticated") {
    return (
      <div className="flex flex-col gap-3 p-4">
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
    );
  }

  const activeLabel = FILTERS.find((f) => f.value === filter)?.label ?? "전체";

  return (
    <div className="flex flex-col gap-3 p-4">
      <h1 className="font-heading">주문 내역</h1>

      <div className="flex gap-2 overflow-x-auto pb-1">
        {FILTERS.map((option) => (
          <button
            key={option.value}
            type="button"
            onClick={() => handleFilterChange(option.value)}
            aria-pressed={filter === option.value}
            className={`font-caption h-9 flex-none rounded-md border px-3 ${
              filter === option.value
                ? "border-brand bg-brand-weak text-brand"
                : "border-border text-text-weak"
            }`}
          >
            {option.label}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
        </div>
      ) : loadError ? (
        <ErrorState message={loadError} onRetry={retryLoad} />
      ) : items.length === 0 ? (
        filter === "ALL" ? (
          <EmptyState
            message="아직 주문이 없어요"
            action={
              <Link
                href="/"
                className="font-body flex h-[52px] w-auto items-center justify-center rounded-md bg-brand px-6 font-medium text-on-brand"
              >
                마감 할인 상품 보러 가기
              </Link>
            }
          />
        ) : (
          <EmptyState
            message={`"${activeLabel}"에 해당하는 주문이 없어요`}
            action={
              <button
                type="button"
                onClick={() => handleFilterChange("ALL")}
                className="font-body flex h-[52px] w-auto items-center justify-center rounded-md border border-border px-6 text-text"
              >
                전체 보기
              </button>
            }
          />
        )
      ) : (
        <ul className="flex flex-col gap-3">
          {items.map((item) => (
            <li key={item.orderId}>
              <Link
                href={`/orders/${item.orderId}`}
                className="block rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]"
              >
                <div className="flex items-center justify-between gap-2">
                  <OrderStatusBadge status={item.status} audience="customer" />
                  <span className="font-caption tabular-nums text-text-weak">{item.orderNo}</span>
                </div>
                <div className="mt-2 flex items-center justify-between gap-2">
                  <span className="font-body text-text">
                    {item.pickupNumber ? `픽업 번호 ${item.pickupNumber}` : "픽업 번호 없음"}
                  </span>
                  <span className="font-price">{formatWon(item.totalAmount)}</span>
                </div>
                {item.pickupStartAt && item.pickupEndAt ? (
                  <p className="font-caption mt-1 text-text-weak">
                    {`픽업 ${formatKstDateTime(item.pickupStartAt)}~${formatKstTime(item.pickupEndAt)}`}
                  </p>
                ) : null}
                <p className="font-caption text-text-weak">
                  {`주문 ${formatKstDateTime(item.orderedAt)}`}
                </p>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
