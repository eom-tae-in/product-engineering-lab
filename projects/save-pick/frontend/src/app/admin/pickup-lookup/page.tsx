import { Suspense } from "react";
import { PickupLookupView } from "@/features/admin-order/components/PickupLookupView";

/**
 * SC-109 · 픽업 번호 조회 (docs/06-screen-list.md §4).
 * `PickupLookupView`가 `useSearchParams()`로 SC-102가 넘긴 `?number=`를 읽는 클라이언트
 * 컴포넌트라 Next.js 16 권장대로 `<Suspense>`로 감싼다(SC-005 `orders/new/page.tsx`와
 * 같은 구조).
 */
export default function PickupLookupPage() {
  return (
    <Suspense
      fallback={
        <div className="p-4">
          <p className="font-body text-text-weak">픽업 번호 조회를 준비하는 중이에요</p>
        </div>
      }
    >
      <PickupLookupView />
    </Suspense>
  );
}
