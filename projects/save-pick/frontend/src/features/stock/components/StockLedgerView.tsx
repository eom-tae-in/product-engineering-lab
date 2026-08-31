"use client";

import { useCallback, useEffect, useState } from "react";
import { fetchStockLedger } from "../api";
import type { StockLedgerItem, StockLedgerReason } from "../types";
import { fetchAdminProductDetail } from "@/features/admin-product/api";
import { formatKstDateTime } from "@/lib/format";
import { Badge } from "@/components/ui/Badge";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Skeleton } from "@/components/ui/Skeleton";

export interface StockLedgerViewProps {
  productId: number;
}

/** docs/06-screen-list.md SC-106 "사유 표기 대응표" 그대로. */
const REASON_LABELS: Record<StockLedgerReason, string> = {
  ADMIN_ADJUST: "관리자 조정",
  HOLD: "선점",
  CONFIRM: "확정 차감",
  HOLD_RELEASE: "선점 해제",
  HOLD_EXPIRE: "선점 만료",
  CANCEL_RESTORE: "취소 복구",
  CANCEL_DISCARD: "취소 폐기",
};

/** 필터 순서도 대응표와 동일하게 둔다(전체 + 7종). */
const REASON_FILTERS: { value: StockLedgerReason | "ALL"; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "ADMIN_ADJUST", label: REASON_LABELS.ADMIN_ADJUST },
  { value: "HOLD", label: REASON_LABELS.HOLD },
  { value: "CONFIRM", label: REASON_LABELS.CONFIRM },
  { value: "HOLD_RELEASE", label: REASON_LABELS.HOLD_RELEASE },
  { value: "HOLD_EXPIRE", label: REASON_LABELS.HOLD_EXPIRE },
  { value: "CANCEL_RESTORE", label: REASON_LABELS.CANCEL_RESTORE },
  { value: "CANCEL_DISCARD", label: REASON_LABELS.CANCEL_DISCARD },
];

const ACTOR_LABELS: Record<StockLedgerItem["actorType"], string> = {
  ADMIN: "관리자",
  CUSTOMER: "고객",
  SYSTEM: "시스템",
};

function formatSignedQuantity(value: number): string {
  return value > 0 ? `+${value}` : `${value}`;
}

/** "변경 수량"에 보여줄 줄. 이 이력 행이 실제로 바꾼 차원(0이 아닌 delta)만 나열한다. */
function buildQuantityChangeLines(item: StockLedgerItem): string[] {
  const lines: string[] = [];
  if (item.deltaTotal !== undefined && item.deltaTotal !== 0) {
    lines.push(`총 재고 ${formatSignedQuantity(item.deltaTotal)}개`);
  }
  if (item.deltaHeld !== undefined && item.deltaHeld !== 0) {
    lines.push(`선점 중 ${formatSignedQuantity(item.deltaHeld)}개`);
  }
  if (item.deltaConfirmed !== undefined && item.deltaConfirmed !== 0) {
    lines.push(`확정 판매 ${formatSignedQuantity(item.deltaConfirmed)}개`);
  }
  return lines;
}

/**
 * "변경 전후 값"에 보여줄 줄. API-111은 실제로 바뀐 차원의 `after*`만 내려주므로
 * (`afterTotal`/`afterAvailable`), 그 차원의 변경 전 값은 같은 행의 delta로 역산한다.
 * `판매 가능`의 변경 전 값은 API-110의 정합성 규칙(`총 재고 = 판매 가능 + 선점 중 +
 * 확정 판매`)에서 유도한 `deltaAvailable = deltaTotal - deltaHeld - deltaConfirmed`로
 * 계산한다.
 */
function buildSnapshotLines(item: StockLedgerItem): string[] {
  const lines: string[] = [];
  if (item.afterTotal !== undefined) {
    const before = item.afterTotal - (item.deltaTotal ?? 0);
    lines.push(`총 재고 ${before} → ${item.afterTotal}`);
  }
  if (item.afterAvailable !== undefined) {
    const deltaAvailable =
      (item.deltaTotal ?? 0) - (item.deltaHeld ?? 0) - (item.deltaConfirmed ?? 0);
    const before = item.afterAvailable - deltaAvailable;
    lines.push(`판매 가능 ${before} → ${item.afterAvailable}`);
  }
  return lines;
}

/**
 * SC-106 · 재고 변경 이력 (docs/06-screen-list.md §4).
 * 관리자 전용 화면이라 `app/admin/layout.tsx`의 AdminGate가 인증을 이미 보장한다.
 * Client Component에서 마운트 시 `authScope: "admin"`으로 직접 호출한다.
 *
 * 사유 필터는 클라이언트에서만 걸러낸다 — API-111에 `reason` 쿼리 파라미터가 없다.
 * 관련 주문 번호는 SC-110(관리자 주문 상세)이 아직 구현되지 않아 링크 없이 텍스트로만
 * 보여준다(작업 지시 범위 밖).
 */
export function StockLedgerView({ productId }: StockLedgerViewProps) {
  const [productName, setProductName] = useState<string | null>(null);
  const [items, setItems] = useState<StockLedgerItem[]>([]);
  const [reasonFilter, setReasonFilter] = useState<StockLedgerReason | "ALL">("ALL");
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // effect 안에서 호출해도 setState가 동기 실행되지 않도록 async/await 대신
  // .then()/.catch() 콜백 안에서만 setState한다(react-hooks/set-state-in-effect,
  // lib/auth/customer-auth.tsx의 refresh().then(...) 패턴과 동일).
  const runLoad = useCallback(() => {
    return Promise.all([fetchStockLedger(productId), fetchAdminProductDetail(productId)])
      .then(([ledger, product]) => {
        setItems(ledger.items);
        setProductName(product.name);
        setLoadError(null);
      })
      .catch(() => {
        setLoadError("불러오지 못했어요");
      })
      .finally(() => {
        setLoading(false);
      });
  }, [productId]);

  // 재시도(버튼 클릭)에서만 쓴다. 로딩 화면으로 되돌린 뒤 다시 불러온다.
  const retryLoad = useCallback(() => {
    setLoading(true);
    setLoadError(null);
    runLoad();
  }, [runLoad]);

  useEffect(() => {
    runLoad();
  }, [runLoad]);

  const visibleItems =
    reasonFilter === "ALL" ? items : items.filter((item) => item.reason === reasonFilter);

  return (
    <div className="flex flex-col gap-3 p-4">
      <div>
        <h1 className="font-heading">재고 변경 이력</h1>
        {productName ? <p className="font-caption text-text-weak">{productName}</p> : null}
      </div>

      <div className="flex flex-wrap gap-2">
        {REASON_FILTERS.map((filter) => (
          <button
            key={filter.value}
            type="button"
            onClick={() => setReasonFilter(filter.value)}
            aria-pressed={reasonFilter === filter.value}
            className={`font-caption h-9 rounded-md border px-3 ${
              reasonFilter === filter.value
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
          <Skeleton className="h-20 w-full" />
        </div>
      ) : loadError ? (
        <ErrorState message={loadError} onRetry={retryLoad} />
      ) : visibleItems.length === 0 ? (
        <EmptyState message="변경 이력이 없어요" />
      ) : (
        <ul className="flex flex-col gap-2">
          {visibleItems.map((item) => (
            <li
              key={item.ledgerId}
              className="rounded-lg border border-border bg-surface p-3 shadow-[var(--shadow-card)]"
            >
              <div className="flex items-center justify-between gap-2">
                <Badge tone="closed">{REASON_LABELS[item.reason]}</Badge>
                <span className="font-caption tabular-nums text-text-weak">
                  {formatKstDateTime(item.occurredAt)}
                </span>
              </div>
              {buildQuantityChangeLines(item).map((line) => (
                <p key={line} className="font-body mt-1 tabular-nums">
                  {line}
                </p>
              ))}
              {buildSnapshotLines(item).map((line) => (
                <p key={line} className="font-caption tabular-nums text-text-weak">
                  {line}
                </p>
              ))}
              <p className="font-caption mt-1 text-text-weak">
                {`변경자 ${ACTOR_LABELS[item.actorType]}`}
                {item.orderNo ? ` · 주문 번호 ${item.orderNo}` : ""}
              </p>
              {item.note ? <p className="font-caption text-text-weak">{item.note}</p> : null}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
