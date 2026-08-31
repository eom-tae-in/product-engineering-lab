"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { nowOnServer } from "@/lib/server-time";
import { formatMmSs } from "@/lib/format";

export interface HoldTimerBarProps {
  /** 서버가 준 선점 만료 시각(ISO 8601 + KST 오프셋). */
  holdExpiresAt: string;
  /** 잔여 시간이 0이 됐을 때 한 번 호출한다. 부모가 만료 시트로 전환한다. */
  onExpire?: () => void;
  /** 매 초 갱신 시 남은 초를 함께 알려준다(예: SC-005 "만료 임박" 배너). */
  onTick?: (remainingSeconds: number) => void;
}

/**
 * docs/09-ui-design-brief.md §2.5 선점 타이머 바 (SC-005, SC-006, SC-007).
 * 높이 44px, 화면 상단 고정. 초 단위로 갱신하고 서버가 준 만료 시각을 `nowOnServer()`
 * (서버-클라이언트 시각 오프셋 보정)로 계산한다 — 기기 시각을 그대로 쓰지 않는다.
 * 0에 도달하면 자기 자신은 사라지고(null 렌더) 부모가 `onExpire`로 만료 시트를 띄운다.
 */
export function HoldTimerBar({ holdExpiresAt, onExpire, onTick }: HoldTimerBarProps) {
  const expiresAtMs = useMemo(() => new Date(holdExpiresAt).getTime(), [holdExpiresAt]);
  const [remainingMs, setRemainingMs] = useState(() => Math.max(0, expiresAtMs - nowOnServer()));
  const hasExpiredRef = useRef(false);

  useEffect(() => {
    hasExpiredRef.current = false;
    const tick = () => setRemainingMs(Math.max(0, expiresAtMs - nowOnServer()));

    // effect 본문에서 setState를 동기 호출하지 않는다(react-hooks/set-state-in-effect).
    // `holdExpiresAt`가 바뀐 직후에도 다음 정시 tick(최대 1초)을 기다리지 않도록
    // 매크로태스크로 한 번 미뤄 초기값을 반영한다.
    const initialTimeout = setTimeout(tick, 0);
    const interval = setInterval(tick, 1000);
    return () => {
      clearTimeout(initialTimeout);
      clearInterval(interval);
    };
  }, [expiresAtMs]);

  const remainingSeconds = Math.ceil(remainingMs / 1000);

  useEffect(() => {
    onTick?.(remainingSeconds);
    if (remainingSeconds <= 0 && !hasExpiredRef.current) {
      hasExpiredRef.current = true;
      onExpire?.();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [remainingSeconds]);

  if (remainingSeconds <= 0) {
    return null;
  }

  const isUrgent = remainingSeconds <= 60;
  const label = formatMmSs(remainingSeconds);

  return (
    <div
      role="status"
      className={`sticky top-0 z-10 flex h-11 shrink-0 items-center justify-center ${
        isUrgent ? "bg-warning-weak text-warning" : "bg-brand-weak text-brand"
      }`}
    >
      <p className="font-body font-medium">
        {isUrgent ? `${label} 남음 · 시간은 연장되지 않아요` : `선점 시간 ${label} 남음`}
      </p>
    </div>
  );
}
