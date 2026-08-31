import { Suspense } from "react";
import { OrderDraftView } from "@/features/order/components/OrderDraftView";

/**
 * SC-005 · 주문서 작성 (docs/06-screen-list.md §3).
 * `OrderDraftView`가 `useSearchParams()`로 `?orderId=`를 읽는 클라이언트 컴포넌트라
 * Next.js 16 권장대로 `<Suspense>`로 감싼다(정적 프리렌더 시 이 훅을 쓰는 트리만
 * 클라이언트 렌더로 분리된다). 폴백 문구는 06번이 정의한 SC-005 로딩 상태와 같다.
 */
export default function OrderDraftPage() {
  return (
    <Suspense
      fallback={
        <div className="flex flex-1 flex-col items-center justify-center p-4 text-center">
          <p className="font-body text-text">재고를 확보하는 중이에요</p>
        </div>
      }
    >
      <OrderDraftView />
    </Suspense>
  );
}
