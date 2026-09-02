"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/customer-auth";
import { fetchOrderDetail } from "../api";
import { fetchNoShowStatus } from "@/features/account/api";
import type { OrderDetailResponse, OrderStatusValue } from "../types";
import { OrderStatusBadge } from "@/components/ui/OrderStatusBadge";
import { Button } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { Skeleton } from "@/components/ui/Skeleton";
import { ApiError } from "@/lib/api-client";
import { formatKstDateTime, formatKstTime, formatWon } from "@/lib/format";

type Router = ReturnType<typeof useRouter>;

export interface OrderDetailViewProps {
  orderId: number;
  /** `/orders/[id]?justConfirmed=1`. 상태가 CONFIRMED일 때만 SC-008 레이아웃으로 그린다. */
  justConfirmed: boolean;
}

/** docs/06-screen-list.md SC-010 "상태 변경 이력(확정 / 준비 완료 / 완료 / 취소 / 노쇼)" 그대로. */
const HISTORY_LABELS: Partial<Record<OrderStatusValue, string>> = {
  CONFIRMED: "확정",
  READY: "준비 완료",
  COMPLETED: "완료",
  CANCELED: "취소",
  NO_SHOW: "노쇼",
};

function pickupTimeText(order: OrderDetailResponse): string | null {
  if (!order.pickupStartAt || !order.pickupEndAt) return null;
  return `${formatKstDateTime(order.pickupStartAt)}~${formatKstTime(order.pickupEndAt)}`;
}

function statusHistoryTime(order: OrderDetailResponse, status: OrderStatusValue): string | null {
  return order.statusHistory.find((entry) => entry.toStatus === status)?.occurredAt ?? null;
}

/**
 * SC-008 · 주문 완료 · SC-010 · 주문 상세 (docs/06-screen-list.md §3).
 * 로그인 필수, 본인 주문만 조회된다(FR-028). 결제 성공 직후 `justConfirmed=1`로
 * 진입하고 서버 상태가 아직 CONFIRMED일 때만 SC-008 레이아웃으로 그린다 — 같은 주문을
 * 시점만 다르게 보여주는 같은 데이터라 한 컴포넌트에서 조건 분기한다(작업 지시서).
 * 액세스 토큰이 클라이언트에만 있어 Client Component에서 마운트 시 직접 호출한다
 * (ARCHITECTURE.md 데이터 페칭 규칙).
 */
export function OrderDetailView({ orderId, justConfirmed }: OrderDetailViewProps) {
  const auth = useAuth();
  const router = useRouter();

  const [order, setOrder] = useState<OrderDetailResponse | null>(null);
  const [noShowCount, setNoShowCount] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorKind, setErrorKind] = useState<"notFound" | "communication" | null>(null);

  const runLoad = useCallback(() => {
    if (!Number.isFinite(orderId)) {
      // 라우트 파라미터가 숫자가 아닌 극히 드문 경우다. fetch를 시도하지 않고 곧바로
      // "조회할 수 없는 주문"으로 처리한다. 다만 setState는 마이크로태스크로 미뤄
      // effect 본문 안에서 동기 실행되지 않게 한다(react-hooks/set-state-in-effect).
      return Promise.resolve().then(() => {
        setErrorKind("notFound");
        setLoading(false);
      });
    }
    return fetchOrderDetail(orderId)
      .then((data) => {
        setOrder(data);
        setErrorKind(null);
        if (data.status !== "NO_SHOW") return undefined;
        return fetchNoShowStatus()
          .then((status) => setNoShowCount(status.recentNoShowCount))
          .catch(() => setNoShowCount(null));
      })
      .catch((error: unknown) => {
        setErrorKind(error instanceof ApiError && error.code === "NOT_FOUND" ? "notFound" : "communication");
      })
      .finally(() => setLoading(false));
  }, [orderId]);

  // 재시도(버튼 클릭)에서만 쓴다. 로딩 화면으로 되돌린 뒤 다시 불러온다.
  const retryLoad = useCallback(() => {
    setLoading(true);
    setErrorKind(null);
    void runLoad();
  }, [runLoad]);

  useEffect(() => {
    if (auth.status === "guest") {
      router.replace("/login");
    }
  }, [auth.status, router]);

  useEffect(() => {
    if (auth.status === "authenticated") {
      void runLoad();
    }
  }, [auth.status, runLoad]);

  if (auth.status !== "authenticated" || loading) {
    return justConfirmed ? <CompleteLoadingSkeleton /> : <DetailLoadingSkeleton />;
  }

  if (errorKind) {
    return justConfirmed ? (
      <CompleteErrorView router={router} />
    ) : (
      <DetailErrorView
        kind={errorKind}
        router={router}
        onRetry={retryLoad}
      />
    );
  }

  if (!order) return null;

  if (justConfirmed && order.status === "CONFIRMED") {
    return <CompleteView order={order} orderId={orderId} router={router} />;
  }

  return <DetailView order={order} orderId={orderId} noShowCount={noShowCount} router={router} />;
}

function CompleteLoadingSkeleton() {
  return (
    <div className="flex flex-1 flex-col gap-3 p-4">
      <Skeleton className="h-6 w-40" />
      <Skeleton className="h-32 w-full" />
      <Skeleton className="h-24 w-full" />
    </div>
  );
}

function DetailLoadingSkeleton() {
  return (
    <div className="flex flex-col gap-3 p-4">
      <Skeleton className="h-32 w-full" />
      <Skeleton className="h-24 w-full" />
      <Skeleton className="h-24 w-full" />
    </div>
  );
}

function CompleteErrorView({ router }: { router: Router }) {
  return (
    <div className="p-4">
      <ErrorState
        message="주문 정보를 불러오지 못했어요. 주문 내역에서 확인해주세요"
        action={
          <Button variant="secondary" className="w-auto px-6" onClick={() => router.push("/orders")}>
            주문 내역으로
          </Button>
        }
      />
    </div>
  );
}

function DetailErrorView({
  kind,
  router,
  onRetry,
}: {
  kind: "notFound" | "communication";
  router: Router;
  onRetry: () => void;
}) {
  if (kind === "notFound") {
    return (
      <div className="p-4">
        <ErrorState
          message="조회할 수 없는 주문이에요"
          action={
            <Button variant="secondary" className="w-auto px-6" onClick={() => router.push("/orders")}>
              주문 내역으로
            </Button>
          }
        />
      </div>
    );
  }
  return (
    <div className="p-4">
      <ErrorState message="주문 정보를 불러오지 못했어요" onRetry={onRetry} />
    </div>
  );
}

function CompleteView({
  order,
  orderId,
  router,
}: {
  order: OrderDetailResponse;
  orderId: number;
  router: Router;
}) {
  return (
    <div className="flex flex-1 flex-col">
      <div className="flex flex-1 flex-col gap-3 p-4">
        <h1 className="font-heading">주문이 확정됐어요</h1>

        <div className="rounded-lg border border-border bg-surface p-4 text-center shadow-[var(--shadow-card)]">
          <p className="font-caption text-text-weak">픽업 번호</p>
          <p className="font-display-pickup text-brand">{order.pickupNumber}</p>
          <p className="font-caption text-text-weak">매장에서 이 번호를 말해주세요</p>
        </div>

        <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
          <dl className="flex flex-col gap-2">
            {pickupTimeText(order) ? (
              <div className="flex justify-between border-b border-border pb-2">
                <dt className="font-caption text-text-weak">픽업</dt>
                <dd className="font-body">{pickupTimeText(order)}</dd>
              </div>
            ) : null}
            <div className="flex justify-between border-b border-border pb-2">
              <dt className="font-caption text-text-weak">결제 금액</dt>
              <dd className="font-price">{formatWon(order.totalAmount)}</dd>
            </div>
            {order.cancelableUntil ? (
              <div className="flex justify-between border-b border-border pb-2">
                <dt className="font-caption text-text-weak">취소 가능</dt>
                <dd className="font-body">{`${formatKstTime(order.cancelableUntil)}까지`}</dd>
              </div>
            ) : null}
            {order.noShowDueAt ? (
              <div className="flex justify-between pb-2">
                <dt className="font-caption text-text-weak">노쇼 전환</dt>
                <dd className="font-body">{formatKstTime(order.noShowDueAt)}</dd>
              </div>
            ) : null}
          </dl>
          {order.cancelableUntil ? (
            <p className="font-caption mt-2 text-text-weak">
              {`${formatKstTime(order.cancelableUntil)}까지 직접 취소할 수 있어요`}
            </p>
          ) : null}
          {order.noShowDueAt ? (
            <p className="font-caption text-text-weak">
              {`${formatKstTime(order.noShowDueAt)}까지 오지 않으면 노쇼로 처리돼요`}
            </p>
          ) : null}
        </div>

        <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
          <div className="flex justify-between">
            <span className="font-caption text-text-weak">주문 번호</span>
            <span className="font-body tabular-nums">{order.orderNo}</span>
          </div>
        </div>

        <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
          <h2 className="font-heading mb-2">{order.store.name}</h2>
          <p className="font-caption text-text-weak">{order.store.address}</p>
          <p className="font-caption text-text-weak">{order.store.phone}</p>
          <Link
            href="/store"
            className="font-body mt-2 flex h-12 items-center justify-center rounded-md border border-border text-text"
          >
            픽업 안내 보기
          </Link>
        </div>
      </div>

      <div className="sticky bottom-0 flex items-center gap-2 border-t border-border bg-surface p-4">
        <Button
          variant="secondary"
          className="flex-1"
          onClick={() => router.push(`/orders/${orderId}`)}
        >
          주문 상세 보기
        </Button>
        <Button className="flex-1" onClick={() => router.push("/")}>
          계속 둘러보기
        </Button>
      </div>
    </div>
  );
}

function DetailView({
  order,
  orderId,
  noShowCount,
  router,
}: {
  order: OrderDetailResponse;
  orderId: number;
  noShowCount: number | null;
  router: Router;
}) {
  const historyEntries = order.statusHistory.filter(
    (entry) => HISTORY_LABELS[entry.toStatus] !== undefined
  );
  const canShowCancelAction = order.status === "CONFIRMED" || order.status === "READY";
  const cancelDeadlinePassedText = `${
    order.cancelableUntil ? formatKstTime(order.cancelableUntil) : ""
  }에 취소 가능 시각이 지났어요. 매장으로 문의해주세요`;

  return (
    <div className="flex flex-1 flex-col">
      {order.status === "READY" ? (
        <div className="bg-brand-weak px-4 py-3">
          <p className="font-body text-brand">매장에서 준비를 마쳤어요</p>
        </div>
      ) : null}
      {order.status === "NO_SHOW" ? (
        <div className="bg-danger-weak px-4 py-3">
          <p className="font-body text-danger">
            {`${order.noShowAt ? formatKstTime(order.noShowAt) : ""}에 노쇼로 처리됐어요. 결제 금액은 환불되지 않아요`}
          </p>
        </div>
      ) : null}
      {order.status === "FAILED" ? (
        <div className="bg-danger-weak px-4 py-3">
          <p className="font-body text-danger">
            결제가 3회 실패해 종료된 주문이에요. 픽업 번호는 발급되지 않았어요
          </p>
        </div>
      ) : null}
      {order.status === "CANCELED" ? (
        <div className="bg-border px-4 py-3">
          <p className="font-body text-text-weak">
            {`${formatKstDateTime(statusHistoryTime(order, "CANCELED") ?? order.orderedAt)}에 취소된 주문이에요`}
          </p>
        </div>
      ) : null}

      <div className="flex flex-1 flex-col gap-3 p-4">
        <div className="rounded-lg border border-border bg-surface p-4 text-center shadow-[var(--shadow-card)]">
          <OrderStatusBadge status={order.status} audience="customer" />
          {order.pickupNumber ? (
            <>
              <p className="font-caption mt-2 text-text-weak">픽업 번호</p>
              <p className="font-display-pickup text-brand">{order.pickupNumber}</p>
            </>
          ) : (
            <p className="font-caption mt-2 text-text-weak">픽업 번호가 없는 주문이에요</p>
          )}
          {pickupTimeText(order) ? (
            <p className="font-caption text-text-weak">{pickupTimeText(order)}</p>
          ) : null}
        </div>

        <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
          <h2 className="font-heading mb-2">{`주문 상품 ${order.items.length}건`}</h2>
          <div className="flex flex-col gap-2">
            {order.items.map((item) => (
              <div key={item.productId}>
                <div className="flex items-center justify-between">
                  <span className="font-body text-text">{`${item.name} ×${item.quantity}`}</span>
                  <span className="font-body font-medium">{formatWon(item.lineAmount)}</span>
                </div>
                <p className="font-caption text-text-weak">{`확정 단가 ${formatWon(item.unitPrice)}`}</p>
              </div>
            ))}
          </div>
          <div className="mt-2 flex justify-between border-t border-border pt-2">
            <span className="font-caption text-text-weak">결제 금액</span>
            <span className="font-price">{formatWon(order.totalAmount)}</span>
          </div>
        </div>

        {canShowCancelAction ? (
          <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
            <dl className="flex flex-col gap-2">
              {order.cancelableUntil ? (
                <div className="flex justify-between border-b border-border pb-2">
                  <dt className="font-caption text-text-weak">취소 가능</dt>
                  <dd className="font-body">{`${formatKstTime(order.cancelableUntil)}까지`}</dd>
                </div>
              ) : null}
              {order.noShowDueAt ? (
                <div className="flex justify-between pb-2">
                  <dt className="font-caption text-text-weak">노쇼 전환</dt>
                  <dd className="font-body">{formatKstTime(order.noShowDueAt)}</dd>
                </div>
              ) : null}
            </dl>
            {order.noShowDueAt ? (
              <p className="font-caption mt-2 text-text-weak">
                {`${formatKstTime(order.noShowDueAt)}까지 오지 않으면 노쇼로 처리돼요`}
              </p>
            ) : null}
          </div>
        ) : null}

        {order.status === "COMPLETED" ? (
          <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
            <p className="font-body text-text">
              {`${
                statusHistoryTime(order, "COMPLETED")
                  ? formatKstTime(statusHistoryTime(order, "COMPLETED") as string)
                  : ""
              }에 픽업이 완료된 주문이에요`}
            </p>
          </div>
        ) : null}

        {order.status === "CANCELED" ? (
          <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
            <dl className="flex flex-col gap-2">
              <div className="flex justify-between border-b border-border pb-2">
                <dt className="font-caption text-text-weak">취소 주체</dt>
                <dd className="font-body">
                  {order.canceledBy === "ADMIN" ? "관리자가 취소했어요" : "고객이 취소했어요"}
                </dd>
              </div>
              {order.canceledBy === "ADMIN" ? (
                <div className="flex justify-between pb-2">
                  <dt className="font-caption text-text-weak">취소 사유</dt>
                  <dd className="font-body">{order.cancelReason ?? "—"}</dd>
                </div>
              ) : null}
            </dl>
          </div>
        ) : null}

        {order.status === "NO_SHOW" ? (
          <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
            <p className="font-caption text-text-weak">{`최근 30일 노쇼 ${noShowCount ?? 0}회`}</p>
          </div>
        ) : null}

        {historyEntries.length > 0 ? (
          <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
            <h2 className="font-heading mb-2">상태 변경 이력</h2>
            <dl className="flex flex-col gap-2">
              {historyEntries.map((entry, index) => (
                <div
                  key={`${entry.toStatus}-${entry.occurredAt}`}
                  className={`flex justify-between pb-2 ${
                    index < historyEntries.length - 1 ? "border-b border-border" : ""
                  }`}
                >
                  <dt className="font-caption text-text-weak">{HISTORY_LABELS[entry.toStatus]}</dt>
                  <dd className="font-body tabular-nums">{formatKstDateTime(entry.occurredAt)}</dd>
                </div>
              ))}
            </dl>
          </div>
        ) : null}

        <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
          <div className="flex justify-between pb-2">
            <span className="font-caption text-text-weak">주문 번호</span>
            <span className="font-body tabular-nums">{order.orderNo}</span>
          </div>
          <Link
            href="/store"
            className="font-body mt-2 flex h-12 items-center justify-center rounded-md border border-border text-text"
          >
            픽업 안내 보기
          </Link>
        </div>
      </div>

      {canShowCancelAction ? (
        <div className="sticky bottom-0 border-t border-border bg-surface p-4">
          <Button
            variant="danger"
            disabled={!order.cancelable}
            disabledReason={
              !order.cancelable && order.cancelUnavailableReason === "CANCEL_DEADLINE_PASSED"
                ? cancelDeadlinePassedText
                : undefined
            }
            onClick={() => router.push(`/orders/${orderId}/cancel`)}
          >
            주문 취소하기
          </Button>
        </div>
      ) : null}
    </div>
  );
}
