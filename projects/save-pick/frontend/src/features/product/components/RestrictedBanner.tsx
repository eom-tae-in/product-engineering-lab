"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth/customer-auth";
import { fetchNoShowStatus } from "@/features/account/api";
import { formatKstDateTime } from "@/lib/format";

/**
 * SC-001 주문 제한 계정 배너 (docs/06-screen-list.md §3 SC-001, FR-032).
 * "상단에 노쇼 3회로 2026-09-04 19:00까지 새 주문을 만들 수 없어요 배너" 문구 그대로.
 * SC-001은 비로그인도 보는 조회 화면(Server Component)이라 이 배너만 별도 클라이언트
 * 컴포넌트로 분리해 로그인 상태일 때만 API-007을 호출한다.
 */
export function RestrictedBanner() {
  const auth = useAuth();
  const [restrictedUntil, setRestrictedUntil] = useState<string | null>(null);
  const [recentNoShowCount, setRecentNoShowCount] = useState(0);

  useEffect(() => {
    // 동기 setState 없이 항상 콜백 안에서만 상태를 바꾼다(react-hooks/set-state-in-effect).
    if (auth.status !== "authenticated") return;
    fetchNoShowStatus()
      .then((status) => {
        if (status.orderPermission === "RESTRICTED" && status.restrictedUntil) {
          setRecentNoShowCount(status.recentNoShowCount);
          setRestrictedUntil(status.restrictedUntil);
        } else {
          setRestrictedUntil(null);
        }
      })
      .catch(() => {
        setRestrictedUntil(null);
      });
  }, [auth.status]);

  if (!restrictedUntil) return null;

  return (
    <div role="status" className="rounded-md bg-warning-weak p-3">
      <p className="font-caption text-warning">
        {`노쇼 ${recentNoShowCount}회로 ${formatKstDateTime(restrictedUntil)}까지 새 주문을 만들 수 없어요`}
      </p>
    </div>
  );
}
