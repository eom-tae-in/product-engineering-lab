"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { fetchAdminProducts, updateProductStatus } from "@/features/admin-product/api";
import type { AdminProductListItem, ProductStatus } from "@/features/admin-product/types";
import { ApiError } from "@/lib/api-client";
import { formatKstTime, formatWon } from "@/lib/format";
import { Button } from "@/components/ui/Button";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Skeleton } from "@/components/ui/Skeleton";

const STATUS_FILTERS: { value: ProductStatus | "ALL"; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "DRAFT", label: "미등록" },
  { value: "ON_SALE", label: "판매중" },
  { value: "HIDDEN", label: "숨김" },
  { value: "CLOSED", label: "마감" },
];

const STATUS_LABELS: Record<ProductStatus, string> = {
  DRAFT: "미등록",
  ON_SALE: "판매중",
  HIDDEN: "숨김",
  CLOSED: "마감",
};

/**
 * 현재 상태에서 다음으로 시도할 수 있는 목표 상태와 버튼 문구(FR-042).
 * CLOSED는 실제로는 어떤 전환도 허용되지 않지만(05 §5.3), 버튼을 숨기지 않고
 * 시도했을 때 `PRODUCT_STATUS_TRANSITION_DENIED`로 안내한다(docs/06 SC-103
 * "오류(CLOSED 되돌리기)").
 */
function nextStatusAction(status: ProductStatus): { target: ProductStatus; label: string } {
  if (status === "ON_SALE") return { target: "HIDDEN", label: "숨기기" };
  if (status === "DRAFT") return { target: "ON_SALE", label: "판매 시작" };
  return { target: "ON_SALE", label: "판매 재개" };
}

/**
 * SC-103 · 상품 관리 목록 (docs/06-screen-list.md §4).
 * 관리자 전용 화면이라 `app/admin/layout.tsx`의 AdminGate가 인증을 이미 보장한다.
 * Client Component에서 마운트 시 `authScope: "admin"`으로 직접 호출한다.
 */
export default function AdminProductListPage() {
  const [statusFilter, setStatusFilter] = useState<ProductStatus | "ALL">("ALL");
  const [items, setItems] = useState<AdminProductListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [statusErrorMessage, setStatusErrorMessage] = useState<string | null>(null);
  const [pendingStatusChange, setPendingStatusChange] = useState<number | null>(null);

  // effect 안에서 호출해도 setState가 동기 실행되지 않도록 async/await 대신
  // .then()/.catch() 콜백 안에서만 setState한다(react-hooks/set-state-in-effect,
  // lib/auth/customer-auth.tsx의 refresh().then(...) 패턴과 동일). 필터 변경도 이
  // effect가 그대로 재조회하므로 필터 버튼 클릭은 상태만 바꾼다.
  const runLoad = useCallback((filter: ProductStatus | "ALL") => {
    return fetchAdminProducts(filter === "ALL" ? {} : { status: filter })
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
  }, []);

  // 재시도(버튼 클릭)에서만 쓴다. 로딩 화면으로 되돌린 뒤 다시 불러온다.
  const retryLoad = useCallback(() => {
    setLoading(true);
    setLoadError(null);
    runLoad(statusFilter);
  }, [runLoad, statusFilter]);

  useEffect(() => {
    runLoad(statusFilter);
  }, [statusFilter, runLoad]);

  async function handleStatusToggle(item: AdminProductListItem) {
    const action = nextStatusAction(item.status);
    setPendingStatusChange(item.productId);
    setStatusErrorMessage(null);
    try {
      await updateProductStatus(item.productId, action.target);
      await runLoad(statusFilter);
    } catch (error) {
      if (error instanceof ApiError && error.code === "PRODUCT_STATUS_TRANSITION_DENIED") {
        setStatusErrorMessage(
          item.status === "CLOSED"
            ? "마감된 상품은 다시 판매할 수 없어요. 새 상품으로 등록해주세요"
            : "재고를 먼저 등록해야 판매할 수 있어요"
        );
      } else if (error instanceof ApiError) {
        setStatusErrorMessage(error.defaultMessage);
      } else {
        setStatusErrorMessage("일시적인 오류로 처리하지 못했어요. 잠시 뒤 다시 시도해주세요.");
      }
    } finally {
      setPendingStatusChange(null);
    }
  }

  return (
    <div className="flex flex-col gap-3 p-4">
      <div className="flex items-center justify-between">
        <h1 className="font-heading">상품 관리</h1>
        <Link
          href="/admin/products/new"
          className="font-body flex h-11 w-auto items-center justify-center rounded-md bg-brand px-4 text-on-brand"
        >
          상품 등록
        </Link>
      </div>

      <div className="flex flex-wrap gap-2">
        {STATUS_FILTERS.map((filter) => (
          <button
            key={filter.value}
            type="button"
            onClick={() => setStatusFilter(filter.value)}
            aria-pressed={statusFilter === filter.value}
            className={`font-caption h-9 rounded-md border px-3 ${
              statusFilter === filter.value
                ? "border-brand bg-brand-weak text-brand"
                : "border-border text-text-weak"
            }`}
          >
            {filter.label}
          </button>
        ))}
      </div>

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
          message="등록된 상품이 없어요"
          action={
            <Link
              href="/admin/products/new"
              className="font-body flex h-11 w-auto items-center justify-center px-6 text-brand"
            >
              상품 등록하기
            </Link>
          }
        />
      ) : (
        <ul className="flex flex-col gap-3">
          {items.map((item) => {
            const action = nextStatusAction(item.status);
            const soldOut = item.availableQuantity === 0;
            return (
              <li
                key={item.productId}
                className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]"
              >
                <div className="flex items-start justify-between gap-2">
                  <Link href={`/admin/products/${item.productId}`} className="min-w-0 flex-1">
                    <p className="font-body truncate">{item.name}</p>
                    <p className="font-caption text-text-weak">{STATUS_LABELS[item.status]}</p>
                  </Link>
                  <Button
                    variant="secondary"
                    className="h-9 w-auto px-3"
                    disabled={pendingStatusChange === item.productId}
                    onClick={() => void handleStatusToggle(item)}
                  >
                    {action.label}
                  </Button>
                </div>
                <dl className="mt-2 grid grid-cols-2 gap-y-1">
                  <dt className="font-caption text-text-weak">정가</dt>
                  <dd className="font-caption text-right tabular-nums">
                    {formatWon(item.originalPrice)}
                  </dd>
                  <dt className="font-caption text-text-weak">현재 할인율</dt>
                  <dd className="font-caption text-right tabular-nums">
                    {`${item.currentDiscountRate}% · ${formatWon(item.currentPrice)}`}
                  </dd>
                  <dt className="font-caption text-text-weak">판매 가능 수량</dt>
                  <dd
                    className={`font-caption text-right tabular-nums ${
                      soldOut ? "text-warning" : ""
                    }`}
                  >
                    {soldOut ? "판매 가능 0" : `${item.availableQuantity}개`}
                  </dd>
                  <dt className="font-caption text-text-weak">마감 시각</dt>
                  <dd className="font-caption text-right tabular-nums">
                    {formatKstTime(item.closingAt)}
                  </dd>
                </dl>
              </li>
            );
          })}
        </ul>
      )}

      <BottomSheet open={statusErrorMessage !== null} onClose={() => setStatusErrorMessage(null)}>
        {statusErrorMessage ? (
          <div className="flex flex-col gap-3">
            <p className="font-body text-text">{statusErrorMessage}</p>
            <Button variant="secondary" onClick={() => setStatusErrorMessage(null)}>
              닫기
            </Button>
          </div>
        ) : null}
      </BottomSheet>
    </div>
  );
}
