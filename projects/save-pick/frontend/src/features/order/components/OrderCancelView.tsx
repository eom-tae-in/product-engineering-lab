"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/customer-auth";
import { cancelOrder, fetchOrderDetail } from "../api";
import type { OrderDetailResponse } from "../types";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { Button } from "@/components/ui/Button";
import { ErrorState } from "@/components/ui/ErrorState";
import { Skeleton } from "@/components/ui/Skeleton";
import { ApiError } from "@/lib/api-client";
import { formatKstDateTime, formatKstTime, formatWon } from "@/lib/format";
import { nowOnServer } from "@/lib/server-time";

type SubmitErrorState =
  | { kind: "deadlinePassed"; time: string }
  | { kind: "notAllowed"; currentStatus: string | null }
  | { kind: "communication" }
  | null;

/**
 * BR-019 — 상품 마감 이후에 취소된 수량은 재고로 되돌리지 않고 폐기로 기록한다.
 * 품목마다 마감 시각이 달라 하나라도 지났으면 그 품목은 되돌아오지 않으므로, 경고를
 * 보여주는 쪽을 택한다(06 SC-011은 마감 전/후 두 문구만 정하고 섞인 경우를 정하지 않았다).
 */
function hasClosedItem(order: OrderDetailResponse): boolean {
  const now = nowOnServer();
  return order.items.some((item) => new Date(item.productClosingAt).getTime() <= now);
}

function pickupTimeText(order: OrderDetailResponse): string | null {
  if (!order.pickupStartAt || !order.pickupEndAt) return null;
  return `${formatKstDateTime(order.pickupStartAt)}~${formatKstTime(order.pickupEndAt)}`;
}

/** docs/06-screen-list.md SC-011 "오류(상태 불일치)" 3분기 문구 표 그대로. */
function statusConflictMessage(currentStatus: string | null): string {
  if (currentStatus === "COMPLETED") return "이미 처리된 주문이에요. 현재 상태는 픽업 완료예요";
  if (currentStatus === "CANCELED") return "이미 취소된 주문이에요";
  if (currentStatus === "NO_SHOW") return "이미 노쇼로 처리된 주문이에요";
  return "지금 상태에서는 취소할 수 없어요";
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between border-b border-border pb-2 last:border-0 last:pb-0">
      <dt className="font-caption text-text-weak">{label}</dt>
      <dd className="font-body">{value}</dd>
    </div>
  );
}

/**
 * SC-011 · 주문 취소 확인 (docs/06-screen-list.md §3).
 * 로그인 필수. CONFIRMED·READY이고 취소 마감 전인 본인 주문만 진입할 수 있다.
 * 마운트 시 주문 상세(API-024)를 다시 불러와 취소 가능 여부를 새로고침에도 견고하게
 * 확인한다 — 이미 취소할 수 없는 상태로 바뀐 뒤 진입했다면(직접 URL 이동 등) 굳이
 * 실패할 취소 요청을 보내지 않고 같은 3분기 문구를 먼저 보여준다.
 */
export function OrderCancelView({ orderId }: { orderId: number }) {
  const auth = useAuth();
  const router = useRouter();

  const [order, setOrder] = useState<OrderDetailResponse | null>(null);
  const [loadError, setLoadError] = useState(false);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<SubmitErrorState>(null);

  const runLoad = useCallback(() => {
    return fetchOrderDetail(orderId)
      .then((data) => {
        setOrder(data);
        setLoadError(false);
      })
      .catch(() => setLoadError(true))
      .finally(() => setLoading(false));
  }, [orderId]);

  // 재시도(버튼 클릭)에서만 쓴다. 로딩 화면으로 되돌린 뒤 다시 불러온다.
  const retryLoad = useCallback(() => {
    setLoading(true);
    setLoadError(false);
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

  async function handleConfirm() {
    setSubmitting(true);
    setSubmitError(null);
    try {
      await cancelOrder(orderId);
      router.replace(`/orders/${orderId}`);
    } catch (error) {
      if (error instanceof ApiError && error.code === "CANCEL_DEADLINE_PASSED") {
        const cancelableUntil = error.details?.cancelableUntil;
        setSubmitError({
          kind: "deadlinePassed",
          time: typeof cancelableUntil === "string" ? formatKstTime(cancelableUntil) : "",
        });
      } else if (error instanceof ApiError && error.code === "CANCEL_NOT_ALLOWED") {
        const currentStatus = error.details?.currentStatus;
        setSubmitError({
          kind: "notAllowed",
          currentStatus: typeof currentStatus === "string" ? currentStatus : null,
        });
      } else {
        setSubmitError({ kind: "communication" });
      }
      setSubmitting(false);
    }
  }

  if (auth.status !== "authenticated" || loading) {
    return (
      <div className="flex flex-col gap-3 p-4">
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
    );
  }

  if (loadError || !order) {
    return (
      <div className="p-4">
        <ErrorState message="주문 정보를 불러오지 못했어요" onRetry={retryLoad} />
      </div>
    );
  }

  const isCurrentlyCancelable =
    (order.status === "CONFIRMED" || order.status === "READY") && order.cancelable;

  if (!isCurrentlyCancelable) {
    const message =
      order.status === "CONFIRMED" || order.status === "READY"
        ? `방금 취소 가능 시각이 지났어요 (${
            order.cancelableUntil ? formatKstTime(order.cancelableUntil) : ""
          })`
        : statusConflictMessage(order.status);

    return (
      <div className="p-4">
        <ErrorState
          message={message}
          action={
            <Button
              variant="secondary"
              className="w-auto px-6"
              onClick={() => router.push(`/orders/${orderId}`)}
            >
              주문 상세로
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <div className="flex flex-1 flex-col gap-3 p-4">
      <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
        <h2 className="font-heading mb-2">취소할 주문</h2>
        <dl className="flex flex-col gap-2">
          <Row label="픽업 번호" value={order.pickupNumber ?? "-"} />
          {pickupTimeText(order) ? (
            <Row label="픽업 시간대" value={pickupTimeText(order) as string} />
          ) : null}
          <Row label="결제 금액" value={formatWon(order.totalAmount)} />
          <Row label="주문 번호" value={order.orderNo} />
        </dl>
      </div>

      <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
        <p className="font-body text-warning">부분 취소는 할 수 없어요. 주문 전체가 취소돼요</p>
        <p className="font-caption mt-2 text-text-weak">
          {hasClosedItem(order)
            ? "마감 시각이 지나 재고로 되돌리지 않아요"
            : "취소한 수량은 바로 다시 판매돼요"}
        </p>
      </div>

      <div className="mt-auto flex flex-col gap-2">
        <Button variant="secondary" onClick={() => router.push(`/orders/${orderId}`)} disabled={submitting}>
          취소하지 않기
        </Button>
        <Button variant="danger" onClick={() => void handleConfirm()} disabled={submitting}>
          {submitting ? "취소 처리 중이에요" : "전체 취소하기"}
        </Button>
      </div>

      <BottomSheet
        open={submitError !== null}
        onClose={() => setSubmitError(null)}
        dismissible={submitError?.kind === "communication"}
      >
        {submitError?.kind === "deadlinePassed" ? (
          <div className="flex flex-col gap-3">
            <h3 className="font-heading">{`방금 취소 가능 시각이 지났어요 (${submitError.time})`}</h3>
            <Button onClick={() => router.push(`/orders/${orderId}`)}>닫기</Button>
          </div>
        ) : submitError?.kind === "notAllowed" ? (
          <div className="flex flex-col gap-3">
            <p className="font-body text-text">{statusConflictMessage(submitError.currentStatus)}</p>
            <Button onClick={() => router.push(`/orders/${orderId}`)}>주문 상세로</Button>
          </div>
        ) : submitError?.kind === "communication" ? (
          <div className="flex flex-col gap-3">
            <p className="font-body text-text">취소하지 못했어요</p>
            <Button onClick={() => void handleConfirm()}>다시 시도</Button>
          </div>
        ) : null}
      </BottomSheet>
    </div>
  );
}
