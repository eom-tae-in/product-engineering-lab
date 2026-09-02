"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { fetchAdminHomeSummary } from "@/features/admin-dashboard/api";
import type { AdminHomeSummary } from "@/features/admin-dashboard/types";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Skeleton } from "@/components/ui/Skeleton";
import { formatKstTime } from "@/lib/format";

interface MenuItem {
  label: string;
  href: string;
}

/**
 * docs/06-screen-list.md SC-102 "메뉴 8개".
 *
 * 판단: "재고 이력"은 06번 §4 SC-102 표시 정보에 메뉴로 나열돼 있지만, 같은 문서
 * SC-102의 실제 "이탈" 경로 목록(SC-103·105·107·108·109·111·112·113)에는 SC-106(재고
 * 변경 이력)이 빠져 있고, SC-106 자신도 "진입: SC-105 `이력 보기`"만 정의한다. 실제
 * 구현(이전 슬라이스)도 상품별 이력 화면만 두고(`/admin/stocks/[productId]/ledger`)
 * 상품을 고르지 않고 바로 열 수 있는 이력 목록 라우트를 만들지 않았다. 그래서 이
 * 메뉴는 이력을 볼 수 있는 실제 진입점인 재고 관리(SC-105, `/admin/stocks`)로
 * 연결한다 — 최종 보고에도 남긴다.
 */
const MENU_ITEMS: MenuItem[] = [
  { label: "상품 관리", href: "/admin/products" },
  { label: "재고 관리", href: "/admin/stocks" },
  { label: "재고 이력", href: "/admin/stocks" },
  { label: "할인 정책", href: "/admin/discount-policy" },
  { label: "주문 관리", href: "/admin/orders" },
  { label: "픽업 현황", href: "/admin/pickup-status" },
  { label: "노쇼", href: "/admin/no-shows" },
  { label: "운영 설정", href: "/admin/settings" },
];

function pickupTimeRangeText(startAt: string, endAt: string): string {
  return `${formatKstTime(startAt)}~${formatKstTime(endAt)}`;
}

/**
 * SC-102 · 관리자 홈 (docs/06-screen-list.md §4).
 * 관리자 전용 화면이라 `app/admin/layout.tsx`의 AdminGate가 인증을 이미 보장한다.
 * Client Component에서 마운트 시 `authScope: "admin"`으로 직접 호출한다
 * (ARCHITECTURE.md 데이터 페칭 규칙).
 *
 * 판단: "오늘 요약"·"다음 픽업 시간대"를 한 번에 내려주는 API가 11번에 없어
 * `features/admin-dashboard/api.ts`가 API-112(×4 상태)·API-110·API-118을 조합한다
 * (그 파일 주석 참고, 최종 보고에도 남긴다).
 */
export default function AdminHomePage() {
  const router = useRouter();
  const [summary, setSummary] = useState<AdminHomeSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [pickupNumberInput, setPickupNumberInput] = useState("");

  const runLoad = useCallback(() => {
    return fetchAdminHomeSummary()
      .then((data) => {
        setSummary(data);
        setLoadError(null);
      })
      .catch(() => {
        setLoadError("요약을 불러오지 못했어요");
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

  function handlePickupNumberSubmit() {
    // 번호를 두 번 입력하게 하지 않으려고 입력값을 넘긴다. SC-109가 이 값을 읽어
    // 진입 즉시 조회한다(`PickupLookupView`).
    router.push(`/admin/pickup-lookup?number=${pickupNumberInput}`);
  }

  const todayOrderTotal = summary
    ? summary.confirmedCount + summary.readyCount + summary.completedCount + summary.noShowCount
    : 0;

  return (
    <div className="flex flex-col gap-3 p-4">
      <h1 className="font-heading">관리자 홈</h1>

      {loading ? (
        <div className="grid grid-cols-2 gap-2">
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
        </div>
      ) : loadError ? (
        <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
          <ErrorState message={loadError} onRetry={retryLoad} />
        </div>
      ) : summary && todayOrderTotal === 0 ? (
        <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
          <EmptyState message="오늘 들어온 주문이 없어요" />
        </div>
      ) : summary ? (
        <>
          <div className="grid grid-cols-2 gap-2">
            <div className="rounded-lg border border-border bg-surface p-3 shadow-[var(--shadow-card)]">
              <p className="font-caption text-text-weak">확정</p>
              <p className="font-heading tabular-nums">{summary.confirmedCount}</p>
            </div>
            <div className="rounded-lg border border-border bg-surface p-3 shadow-[var(--shadow-card)]">
              <p className="font-caption text-text-weak">준비 완료</p>
              <p className="font-heading tabular-nums">{summary.readyCount}</p>
            </div>
            <div className="rounded-lg border border-border bg-surface p-3 shadow-[var(--shadow-card)]">
              <p className="font-caption text-text-weak">픽업 완료</p>
              <p className="font-heading tabular-nums">{summary.completedCount}</p>
            </div>
            <div className="rounded-lg border border-border bg-surface p-3 shadow-[var(--shadow-card)]">
              <p className="font-caption text-text-weak">노쇼</p>
              <p className="font-heading tabular-nums text-danger">{summary.noShowCount}</p>
            </div>
            <div className="col-span-2 rounded-lg border border-border bg-surface p-3 shadow-[var(--shadow-card)]">
              <p className="font-caption text-text-weak">판매 가능 0인 상품</p>
              <p className="font-heading tabular-nums text-warning">
                {summary.unavailableProductCount}
              </p>
            </div>
          </div>

          {summary.nextSlot ? (
            <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
              <h2 className="font-heading mb-2">다음 픽업 시간대</h2>
              <p className="font-body tabular-nums">
                {`${pickupTimeRangeText(summary.nextSlot.startAt, summary.nextSlot.endAt)} · 예약 ${summary.nextSlot.reservedCount}/${summary.nextSlot.capacity}건`}
              </p>
              <Link
                href="/admin/pickup-status"
                className="font-body mt-3 flex h-12 items-center justify-center rounded-md border border-border text-text"
              >
                픽업 현황 보기
              </Link>
            </div>
          ) : null}
        </>
      ) : null}

      <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
        <h2 className="font-heading mb-2">픽업 번호 조회</h2>
        <div className="flex gap-2">
          <input
            aria-label="픽업 번호 3자리"
            inputMode="numeric"
            maxLength={3}
            value={pickupNumberInput}
            onChange={(event) =>
              setPickupNumberInput(event.target.value.replace(/\D/g, "").slice(0, 3))
            }
            className="font-body h-[52px] flex-1 rounded-md border border-border bg-surface px-3 text-center text-text outline-none focus:border-brand"
          />
          <Button
            className="w-24"
            onClick={handlePickupNumberSubmit}
            disabled={pickupNumberInput.length === 0}
          >
            조회
          </Button>
        </div>
        <p className="font-caption mt-2 text-text-weak">오늘 영업일 기준으로 찾아요</p>
      </div>

      <h2 className="font-heading">관리 메뉴</h2>
      <ul className="flex flex-col divide-y divide-border rounded-lg border border-border bg-surface shadow-[var(--shadow-card)]">
        {MENU_ITEMS.map((item) => (
          <li key={item.label}>
            <Link
              href={item.href}
              className="font-body flex h-14 items-center justify-between px-4 text-text"
            >
              <span>{item.label}</span>
              <span className="font-caption text-text-weak">{"›"}</span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
