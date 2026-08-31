import { Suspense } from "react";
import { PaymentView } from "@/features/order/components/PaymentView";
import { Skeleton } from "@/components/ui/Skeleton";

/**
 * SC-007 · 결제 확인 및 결과 (docs/06-screen-list.md §3).
 * `PaymentView`가 `useSearchParams()`를 쓰는 클라이언트 컴포넌트라 `<Suspense>`로
 * 감싼다(Next.js 16 권장 패턴).
 */
export default function PaymentPage() {
  return (
    <Suspense
      fallback={
        <div className="flex flex-1 flex-col gap-3 p-4">
          <Skeleton className="h-11 w-full" />
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-24 w-full" />
        </div>
      }
    >
      <PaymentView />
    </Suspense>
  );
}
