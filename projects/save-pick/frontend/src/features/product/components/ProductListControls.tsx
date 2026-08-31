"use client";

import { usePathname, useRouter } from "next/navigation";
import type { ProductSort } from "../types";

const SORT_OPTIONS: { value: ProductSort; label: string }[] = [
  { value: "CLOSING_SOON", label: "마감 임박 순" },
  { value: "DISCOUNT_DESC", label: "할인율 높은 순" },
  { value: "PRICE_ASC", label: "가격 낮은 순" },
];

export interface ProductListControlsProps {
  sort: ProductSort;
  hideSoldOut: boolean;
}

/**
 * SC-001 정렬 바 + 품절 숨기기 토글 (docs/06-screen-list.md §3 SC-001 주요 액션, FR-012).
 * 이 화면은 Server Component에서 직접 fetch하므로(ARCHITECTURE.md 데이터 페칭 규칙),
 * 정렬·필터를 바꾸면 쿼리스트링을 갱신해 같은 라우트를 다시 요청한다.
 */
export function ProductListControls({ sort, hideSoldOut }: ProductListControlsProps) {
  const router = useRouter();
  const pathname = usePathname();

  function navigate(nextSort: ProductSort, nextHideSoldOut: boolean) {
    const query = new URLSearchParams();
    query.set("sort", nextSort);
    if (nextHideSoldOut) query.set("hideSoldOut", "true");
    router.push(`${pathname}?${query.toString()}`);
  }

  return (
    <div className="flex items-center justify-between">
      <label className="font-caption text-text-weak">
        <span className="sr-only">정렬</span>
        <select
          aria-label="정렬"
          value={sort}
          onChange={(event) => navigate(event.target.value as ProductSort, hideSoldOut)}
          className="rounded-md border border-border bg-surface px-2 py-1"
        >
          {SORT_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </label>
      <label className="font-caption flex items-center gap-2 text-text-weak">
        <input
          type="checkbox"
          checked={hideSoldOut}
          onChange={(event) => navigate(sort, event.target.checked)}
        />
        품절 숨기기
      </label>
    </div>
  );
}
