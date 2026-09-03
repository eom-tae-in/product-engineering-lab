"use client";

import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/lib/auth/customer-auth";
import { fetchOrderDetail, fetchOrders } from "@/features/order/api";
import type { OrderDetailResponse, OrderListItem } from "@/features/order/types";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Skeleton } from "@/components/ui/Skeleton";
import { formatKstDateTime, formatKstTime } from "@/lib/format";

/**
 * 픽업 시간대가 정해진 주문 중 가장 이른 것을 고른다. 06번은 "확정 주문이 있으면"이라고만
 * 하는데, 진행 중 주문이 여럿이면 매장 앞에서 궁금한 건 다음에 받을 한 건이다.
 */
function nearestPickup(items: OrderListItem[]): OrderListItem | null {
  const scheduled = items.filter((item) => item.pickupStartAt !== null);
  if (scheduled.length === 0) return null;
  return scheduled.reduce((earliest, item) =>
    (item.pickupStartAt as string) < (earliest.pickupStartAt as string) ? item : earliest
  );
}

/**
 * SC-015 「예정된 픽업」 영역 (docs/06-screen-list.md §3 표시 정보 3번째 줄).
 *
 * 이 화면 자체는 비로그인도 볼 수 있는 Server Component지만, 확정 주문은 본인 것만
 * 조회되는 자원이라 액세스 토큰이 있는 클라이언트에서만 불러올 수 있다
 * (ARCHITECTURE.md 데이터 페칭 규칙). 그래서 이 영역만 Client Component로 분리했다.
 * 비로그인이어도 로그인으로 보내지 않는다 — SC-015는 권한 제한이 없는 화면이라
 * 빈 상태를 그대로 보여준다.
 *
 * 판단: 노쇼 전환 예정 시각은 API-023(주문 내역) 응답에 없고 API-024(주문 상세)에만
 * 있다. 목록에서 가장 이른 픽업 한 건을 고른 뒤 그 건의 상세를 한 번 더 부른다 —
 * 표시 대상이 한 건뿐이라 추가 호출이 한 번이고, 11번 계약을 바꾸지 않아도 된다.
 */
export function UpcomingPickupCard() {
  const auth = useAuth();
  const [order, setOrder] = useState<OrderDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);

  const runLoad = useCallback(() => {
    return fetchOrders({ status: "IN_PROGRESS" })
      .then((list) => {
        const next = nearestPickup(list.items);
        if (!next) {
          setOrder(null);
          setLoadFailed(false);
          return undefined;
        }
        return fetchOrderDetail(next.orderId).then((detail) => {
          setOrder(detail);
          setLoadFailed(false);
        });
      })
      .catch(() => setLoadFailed(true))
      .finally(() => setLoading(false));
  }, []);

  const retryLoad = useCallback(() => {
    setLoading(true);
    setLoadFailed(false);
    void runLoad();
  }, [runLoad]);

  useEffect(() => {
    if (auth.status === "authenticated") {
      void runLoad();
    }
  }, [auth.status, runLoad]);

  // 비로그인은 아무것도 부르지 않으므로 `loading`이 true로 남지만, 아래 조건이
  // authenticated일 때만 스켈레톤을 그리므로 그대로 빈 상태로 떨어진다 —
  // effect에서 setState를 직접 호출하지 않기 위해서다(react-hooks/set-state-in-effect).
  if (auth.status === "checking" || (auth.status === "authenticated" && loading)) {
    return <Skeleton className="h-20 w-full" />;
  }

  if (loadFailed) {
    // 06번은 이 영역의 오류 문구를 정하지 않았다. 형제 화면들과 같은 공통 문구를 쓴다.
    return <ErrorState message="예정된 픽업을 불러오지 못했어요" onRetry={retryLoad} />;
  }

  if (!order) {
    return <EmptyState message="예정된 픽업이 없어요" />;
  }

  return (
    <dl className="flex flex-col gap-2">
      {order.pickupNumber ? (
        <div className="flex justify-between border-b border-border pb-2">
          <dt className="font-caption text-text-weak">픽업 번호</dt>
          <dd className="font-body tabular-nums">{order.pickupNumber}</dd>
        </div>
      ) : null}
      {order.pickupStartAt && order.pickupEndAt ? (
        <div className="flex justify-between border-b border-border pb-2">
          <dt className="font-caption text-text-weak">픽업 시간대</dt>
          <dd className="font-body tabular-nums">
            {`${formatKstDateTime(order.pickupStartAt)}~${formatKstTime(order.pickupEndAt)}`}
          </dd>
        </div>
      ) : null}
      {order.noShowDueAt ? (
        <div className="flex justify-between pb-2">
          <dt className="font-caption text-text-weak">노쇼 전환 예정</dt>
          <dd className="font-body tabular-nums">{formatKstTime(order.noShowDueAt)}</dd>
        </div>
      ) : null}
    </dl>
  );
}
