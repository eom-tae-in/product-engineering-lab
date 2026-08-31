import { Suspense } from "react";
import { PickupSlotSelectionView } from "@/features/order/components/PickupSlotSelectionView";
import { Skeleton } from "@/components/ui/Skeleton";

/**
 * SC-006 · 픽업 날짜·시간대 선택 (docs/06-screen-list.md §3).
 * `PickupSlotSelectionView`가 `useSearchParams()`를 쓰는 클라이언트 컴포넌트라
 * `<Suspense>`로 감싼다(Next.js 16 권장 패턴).
 */
export default function PickupSlotSelectionPage() {
  return (
    <Suspense
      fallback={
        <div className="flex flex-1 flex-col gap-3 p-4">
          <Skeleton className="h-11 w-full" />
          <div className="grid grid-cols-3 gap-2">
            {Array.from({ length: 12 }, (_, index) => (
              <Skeleton key={index} className="h-14 w-[100px]" />
            ))}
          </div>
        </div>
      }
    >
      <PickupSlotSelectionView />
    </Suspense>
  );
}
