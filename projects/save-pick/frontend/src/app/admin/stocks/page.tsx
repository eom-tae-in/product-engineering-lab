"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { adjustStock, fetchAdminStocks } from "@/features/stock/api";
import type { StockListItem } from "@/features/stock/types";
import { ApiError } from "@/lib/api-client";
import { Button } from "@/components/ui/Button";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Skeleton } from "@/components/ui/Skeleton";
import { TextField } from "@/components/ui/TextField";

const FILTERS: { value: boolean; label: string }[] = [
  { value: false, label: "전체" },
  { value: true, label: "판매 가능 0" },
];

/** BR-025: 총 재고는 선점 중 + 확정 판매보다 작게 줄일 수 없다. 서버 응답 없이도 계산 가능한 하한값. */
function minimumSettableQuantityOf(item: StockListItem): number {
  return item.heldQuantity + item.confirmedQuantity;
}

/**
 * SC-105 · 재고 현황·조정 (docs/06-screen-list.md §4).
 * 관리자 전용 화면이라 `app/admin/layout.tsx`의 AdminGate가 인증을 이미 보장한다.
 * Client Component에서 마운트 시 `authScope: "admin"`으로 직접 호출한다.
 */
export default function AdminStockListPage() {
  const [onlyUnavailable, setOnlyUnavailable] = useState(false);
  const [items, setItems] = useState<StockListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [adjustTarget, setAdjustTarget] = useState<StockListItem | null>(null);
  const [adjustValue, setAdjustValue] = useState("");
  const [adjustError, setAdjustError] = useState<string | null>(null);
  const [adjustSaving, setAdjustSaving] = useState(false);

  // effect 안에서 호출해도 setState가 동기 실행되지 않도록 async/await 대신
  // .then()/.catch() 콜백 안에서만 setState한다(react-hooks/set-state-in-effect,
  // lib/auth/customer-auth.tsx의 refresh().then(...) 패턴과 동일). 필터 변경도 이
  // effect가 그대로 재조회하므로 필터 버튼 클릭은 상태만 바꾼다.
  const runLoad = useCallback((filter: boolean) => {
    return fetchAdminStocks(filter ? { onlyUnavailable: true } : {})
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
    runLoad(onlyUnavailable);
  }, [runLoad, onlyUnavailable]);

  useEffect(() => {
    runLoad(onlyUnavailable);
  }, [onlyUnavailable, runLoad]);

  function openAdjustSheet(item: StockListItem) {
    setAdjustTarget(item);
    setAdjustValue(String(item.totalQuantity));
    setAdjustError(null);
  }

  function closeAdjustSheet() {
    setAdjustTarget(null);
    setAdjustValue("");
    setAdjustError(null);
  }

  async function submitAdjust() {
    if (!adjustTarget) return;

    if (!/^\d+$/.test(adjustValue.trim())) {
      setAdjustError("0 이상 정수만 입력할 수 있어요");
      return;
    }

    setAdjustSaving(true);
    setAdjustError(null);
    try {
      await adjustStock(adjustTarget.productId, { totalQuantity: Number(adjustValue) });
      closeAdjustSheet();
      await runLoad(onlyUnavailable);
    } catch (error) {
      if (error instanceof ApiError && error.code === "STOCK_BELOW_COMMITTED") {
        const minimum =
          (error.details?.minimumSettableQuantity as number | undefined) ??
          minimumSettableQuantityOf(adjustTarget);
        setAdjustError(
          `확정 ${adjustTarget.confirmedQuantity}개와 선점 ${adjustTarget.heldQuantity}개가 있어 ${minimum}개 미만으로 줄일 수 없어요`
        );
      } else if (error instanceof ApiError && error.code === "VALIDATION_ERROR") {
        setAdjustError("0 이상 정수만 입력할 수 있어요");
      } else if (error instanceof ApiError) {
        setAdjustError(error.defaultMessage);
      } else {
        setAdjustError("일시적인 오류로 처리하지 못했어요. 잠시 뒤 다시 시도해주세요.");
      }
    } finally {
      setAdjustSaving(false);
    }
  }

  return (
    <div className="flex flex-col gap-3 p-4">
      <h1 className="font-heading">재고 현황</h1>

      <div className="flex flex-wrap gap-2">
        {FILTERS.map((filter) => (
          <button
            key={String(filter.value)}
            type="button"
            onClick={() => setOnlyUnavailable(filter.value)}
            aria-pressed={onlyUnavailable === filter.value}
            className={`font-caption h-9 rounded-md border px-3 ${
              onlyUnavailable === filter.value
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
          <Skeleton className="h-28 w-full" />
          <Skeleton className="h-28 w-full" />
          <Skeleton className="h-28 w-full" />
          <Skeleton className="h-28 w-full" />
        </div>
      ) : loadError ? (
        <ErrorState message={loadError} onRetry={retryLoad} />
      ) : items.length === 0 ? (
        <EmptyState message="재고를 등록한 상품이 없어요" />
      ) : (
        <ul className="flex flex-col gap-3">
          {items.map((item) => {
            const soldOut = item.availableQuantity === 0;
            return (
              <li
                key={item.productId}
                className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]"
              >
                <div className="mb-2 flex items-center justify-between gap-2">
                  <p className="font-body truncate font-semibold">{item.name}</p>
                  <div className="flex items-center gap-3">
                    <Link
                      href={`/admin/stocks/${item.productId}/ledger`}
                      className="font-caption text-brand"
                    >
                      이력 보기
                    </Link>
                    <Button
                      variant="secondary"
                      className="h-9 w-auto px-3"
                      onClick={() => openAdjustSheet(item)}
                    >
                      조정
                    </Button>
                  </div>
                </div>
                <table className="w-full">
                  <thead>
                    <tr className="font-caption text-left text-text-weak">
                      <th scope="col" className="pb-1">
                        총 재고
                      </th>
                      <th scope="col" className="pb-1 text-right">
                        판매 가능
                      </th>
                      <th scope="col" className="pb-1 text-right">
                        선점 중
                      </th>
                      <th scope="col" className="pb-1 text-right">
                        확정 판매
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr className="font-body">
                      <td className="tabular-nums">{item.totalQuantity}</td>
                      <td className="tabular-nums text-right">{item.availableQuantity}</td>
                      <td className="tabular-nums text-right">{item.heldQuantity}</td>
                      <td className="tabular-nums text-right">{item.confirmedQuantity}</td>
                    </tr>
                  </tbody>
                </table>
                {soldOut ? <p className="font-caption mt-1 text-warning">판매 가능 0</p> : null}
                <p className="font-caption mt-1 text-text-weak">
                  {`총 재고 ${item.totalQuantity} = 판매 가능 ${item.availableQuantity} + 선점 중 ${item.heldQuantity} + 확정 판매 ${item.confirmedQuantity}`}
                </p>
              </li>
            );
          })}
        </ul>
      )}

      <BottomSheet open={adjustTarget !== null} onClose={closeAdjustSheet}>
        {adjustTarget ? (
          <div className="flex flex-col gap-3">
            <h2 className="font-heading">{`${adjustTarget.name} 재고 조정`}</h2>
            <p className="font-caption text-text-weak">
              {`현재 총 재고 ${adjustTarget.totalQuantity}개 · 선점 중 ${adjustTarget.heldQuantity}개 · 확정 판매 ${adjustTarget.confirmedQuantity}개`}
            </p>
            <div>
              <TextField
                id="adjust-total-quantity"
                label="새 총 재고"
                inputMode="numeric"
                value={adjustValue}
                onChange={(event) => setAdjustValue(event.target.value)}
                error={adjustError ?? undefined}
                disabled={adjustSaving}
              />
              {!adjustError ? (
                <p className="font-caption -mt-3 text-text-weak">
                  {`설정 가능한 최소값 ${minimumSettableQuantityOf(adjustTarget)}개`}
                </p>
              ) : null}
            </div>
            <Button onClick={() => void submitAdjust()} disabled={adjustSaving}>
              {adjustSaving ? "저장하는 중이에요" : "저장"}
            </Button>
            <Button variant="secondary" onClick={closeAdjustSheet} disabled={adjustSaving}>
              닫기
            </Button>
          </div>
        ) : null}
      </BottomSheet>
    </div>
  );
}
