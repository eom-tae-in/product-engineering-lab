"use client";

import { useEffect } from "react";
import { fetchServerTimeOffsetMs, setServerTimeOffsetMs } from "@/lib/server-time";

/**
 * ARCHITECTURE.md §서버 시각 동기화: 앱 로드 시 한 번 API-008로 서버-클라이언트
 * 시각 차이(offset)를 계산해 `lib/server-time.ts`에 보관한다. 선점 타이머
 * (SC-005~007, `HoldTimerBar`)가 `nowOnServer()`로 "지금"을 계산할 때 이 offset을
 * 쓴다 — 클라이언트 시계가 틀려도 서버 판정과 어긋나지 않게 하기 위해서다(FR-005).
 *
 * 화면을 그리지 않는 순수 부수효과 컴포넌트라 `(customer)/layout.tsx`에 한 번만
 * 둔다. 오프셋 계산이 실패해도(네트워크 오류 등) offset은 0으로 남아 기기 시각을
 * 그대로 쓰는 것과 같아지므로 화면을 막지 않는다.
 */
export function ServerTimeSync() {
  useEffect(() => {
    fetchServerTimeOffsetMs()
      .then(setServerTimeOffsetMs)
      .catch(() => {
        // 서버 시각 조회에 실패해도 offset 0(기기 시각 그대로)으로 동작을 계속한다.
      });
  }, []);

  return null;
}
