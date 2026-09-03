"use client";

import { useCallback, useEffect, useState } from "react";
import {
  cancelAdminOrder,
  completeOrderPickup,
  fetchAdminOrderDetail,
  markOrderReady,
} from "../api";
import type { AdminOrderDetailResponse } from "../types";
import type { OrderStatusValue } from "@/features/order/types";
import { OrderStatusBadge } from "@/components/ui/OrderStatusBadge";
import { Button } from "@/components/ui/Button";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { ErrorState } from "@/components/ui/ErrorState";
import { Skeleton } from "@/components/ui/Skeleton";
import { ApiError } from "@/lib/api-client";
import { formatKstDateTime, formatKstTime, formatWon } from "@/lib/format";
import { nowOnServer } from "@/lib/server-time";

export interface AdminOrderDetailViewProps {
  orderId: number;
}

/** docs/06-screen-list.md SC-110 "상태 변경 이력"에 쓴다. `wireframes/sc-110-admin-order-detail.html`과 라벨을 맞췄다. */
const HISTORY_LABELS: Partial<Record<OrderStatusValue, string>> = {
  CONFIRMED: "확정",
  READY: "준비 완료",
  COMPLETED: "픽업 완료",
  CANCELED: "취소",
  NO_SHOW: "노쇼",
};

const PAYMENT_ATTEMPT_LABELS: Record<"SUCCEEDED" | "FAILED", string> = {
  SUCCEEDED: "성공",
  FAILED: "실패",
};

function pickupTimeText(order: AdminOrderDetailResponse): string | null {
  if (!order.pickupStartAt || !order.pickupEndAt) return null;
  return `${formatKstDateTime(order.pickupStartAt)}~${formatKstTime(order.pickupEndAt)}`;
}

function statusHistoryTime(
  order: AdminOrderDetailResponse,
  status: OrderStatusValue
): string | null {
  return order.statusHistory.find((entry) => entry.toStatus === status)?.occurredAt ?? null;
}

/**
 * BR-019 — 상품 마감 이후 취소분은 재고로 되돌리지 않고 폐기로 기록한다. 품목마다 마감이
 * 달라 하나라도 지났으면 그 품목은 되돌아오지 않으므로 경고 쪽 문구를 보여준다
 * (고객 화면 `OrderCancelView`와 같은 판단).
 */
function hasClosedItem(order: AdminOrderDetailResponse): boolean {
  const now = nowOnServer();
  return order.items.some((item) => new Date(item.productClosingAt).getTime() <= now);
}

function totalQuantityOf(order: AdminOrderDetailResponse): number {
  return order.items.reduce((sum, item) => sum + item.quantity, 0);
}

/**
 * 취소 불가 안내 문구. 06 SC-110은 COMPLETED 문구만 못박아, 나머지 상태는 문구를 새로
 * 지어내지 않고 이미 승인된 원문(SC-011)과 오류 코드 카탈로그 기본 문구를 쓴다.
 */
function cancelNotAllowedMessage(
  currentStatus: OrderStatusValue | undefined,
  fallbackMessage: string
): string {
  if (currentStatus === "COMPLETED") return "픽업 완료된 주문은 취소할 수 없어요";
  if (currentStatus === "CANCELED") return "이미 취소된 주문이에요";
  return fallbackMessage;
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
 * SC-110 · 주문 상세 (관리자) (docs/06-screen-list.md §4).
 * 관리자 전용 화면이라 `app/admin/layout.tsx`의 AdminGate가 인증을 이미 보장한다.
 * Client Component에서 마운트 시 `authScope: "admin"`으로 직접 호출한다
 * (ARCHITECTURE.md 데이터 페칭 규칙).
 *
 * 판단: 06번은 이 화면의 "오류(조회 실패)" 문구를 못박지 않아, 형제 관리자 상세 화면인
 * SC-104(`AdminProductEditView`)와 같은 공통 문구("불러오지 못했어요" + 다시 시도)로
 * 통일했다.
 */
export function AdminOrderDetailView({ orderId }: AdminOrderDetailViewProps) {
  const [order, setOrder] = useState<AdminOrderDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [actionSubmitting, setActionSubmitting] = useState<"READY" | "COMPLETE" | null>(null);
  const [actionErrorSheet, setActionErrorSheet] = useState<string | null>(null);

  const [cancelSheetOpen, setCancelSheetOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState("");
  const [cancelFieldError, setCancelFieldError] = useState<string | null>(null);
  const [cancelSubmitting, setCancelSubmitting] = useState(false);
  const [cancelErrorSheet, setCancelErrorSheet] = useState<string | null>(null);

  // effect 안에서 호출해도 setState가 동기 실행되지 않도록 async/await 대신
  // .then()/.catch() 콜백 안에서만 setState한다(react-hooks/set-state-in-effect,
  // lib/auth/customer-auth.tsx의 refresh().then(...) 패턴과 동일).
  const runLoad = useCallback(() => {
    return fetchAdminOrderDetail(orderId)
      .then((data) => {
        setOrder(data);
        setLoadError(null);
      })
      .catch(() => {
        setLoadError("불러오지 못했어요");
      })
      .finally(() => {
        setLoading(false);
      });
  }, [orderId]);

  // 재시도(버튼 클릭)에서만 쓴다. 로딩 화면으로 되돌린 뒤 다시 불러온다.
  const retryLoad = useCallback(() => {
    setLoading(true);
    setLoadError(null);
    void runLoad();
  }, [runLoad]);

  useEffect(() => {
    void runLoad();
  }, [runLoad]);

  // 액션(준비 완료·완료·취소) 성공 뒤에는 화면 상태를 부분 병합하지 않고 상세를 다시
  // 불러온다 — AdminStockListPage·AdminProductListPage와 동일한 패턴이다.
  // 갱신된 주문을 돌려줘 호출부가 실제 서버 상태로 문구를 고를 수 있게 한다.
  function reloadAfterAction(): Promise<AdminOrderDetailResponse | null> {
    return fetchAdminOrderDetail(orderId)
      .then((data) => {
        setOrder(data);
        return data;
      })
      .catch(() => {
        // 갱신 실패는 무시한다 — 화면엔 직전 데이터가 그대로 남는다.
        return null;
      });
  }

  async function handleReady() {
    setActionSubmitting("READY");
    setActionErrorSheet(null);
    try {
      await markOrderReady(orderId);
      await reloadAfterAction();
    } catch (error) {
      if (error instanceof ApiError && error.code === "INVALID_ORDER_STATUS") {
        setActionErrorSheet("CONFIRMED 주문만 준비 완료로 바꿀 수 있어요");
      } else if (error instanceof ApiError) {
        setActionErrorSheet(error.defaultMessage);
      } else {
        setActionErrorSheet("처리하지 못했어요");
      }
      await reloadAfterAction();
    } finally {
      setActionSubmitting(null);
    }
  }

  async function handleComplete() {
    setActionSubmitting("COMPLETE");
    setActionErrorSheet(null);
    try {
      await completeOrderPickup(orderId);
      await reloadAfterAction();
    } catch (error) {
      if (error instanceof ApiError && error.code === "INVALID_ORDER_STATUS") {
        const currentStatus =
          typeof error.details?.currentStatus === "string" ? error.details.currentStatus : undefined;
        setActionErrorSheet(
          currentStatus === "NO_SHOW"
            ? "노쇼로 전환된 주문은 완료 처리할 수 없어요"
            : "이미 완료 처리된 주문이에요"
        );
      } else if (error instanceof ApiError) {
        setActionErrorSheet(error.defaultMessage);
      } else {
        setActionErrorSheet("처리하지 못했어요");
      }
      await reloadAfterAction();
    } finally {
      setActionSubmitting(null);
    }
  }

  function openCancelSheet() {
    setCancelReason("");
    setCancelFieldError(null);
    setCancelErrorSheet(null);
    setCancelSheetOpen(true);
  }

  function closeCancelSheet() {
    setCancelSheetOpen(false);
    setCancelReason("");
    setCancelFieldError(null);
  }

  async function submitCancel() {
    const trimmed = cancelReason.trim();
    if (!trimmed) {
      // docs/06 SC-110 "오류(사유 미입력)": 취소를 실행하지 않는다(FR-054).
      setCancelFieldError("취소 사유를 입력해주세요");
      return;
    }
    setCancelFieldError(null);
    setCancelSubmitting(true);
    try {
      await cancelAdminOrder(orderId, trimmed);
      setCancelSheetOpen(false);
      await reloadAfterAction();
    } catch (error) {
      if (error instanceof ApiError && error.code === "CANCEL_REASON_REQUIRED") {
        setCancelFieldError("취소 사유를 입력해주세요");
      } else if (error instanceof ApiError && error.code === "CANCEL_NOT_ALLOWED") {
        setCancelSheetOpen(false);
        // 11번 API-117의 CANCEL_NOT_ALLOWED는 COMPLETED·NO_SHOW·CANCELED를 한 코드로
        // 묶고 `details.currentStatus`도 담지 않는다. 화면이 아는 상태는 이미 낡았으므로
        // (그래서 이 오류가 났다) 상세를 다시 읽어 실제 상태로 문구를 고른다 —
        // 이미 취소된 주문에 "픽업 완료된 주문"처럼 사실과 다른 문구를 보여주지 않기
        // 위해서다(06 SC-011이 같은 이유로 상태별 분기를 요구한다).
        const refreshed = await reloadAfterAction();
        setCancelErrorSheet(cancelNotAllowedMessage(refreshed?.status, error.defaultMessage));
      } else if (error instanceof ApiError) {
        setCancelSheetOpen(false);
        setCancelErrorSheet(error.defaultMessage);
      } else {
        setCancelSheetOpen(false);
        setCancelErrorSheet("처리하지 못했어요");
      }
    } finally {
      setCancelSubmitting(false);
    }
  }

  if (loading) {
    return (
      <div className="flex flex-col gap-3 p-4">
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
    );
  }

  if (loadError || !order) {
    return (
      <div className="p-4">
        <ErrorState message={loadError ?? "불러오지 못했어요"} onRetry={retryLoad} />
      </div>
    );
  }

  const historyEntries = order.statusHistory.filter(
    (entry) => HISTORY_LABELS[entry.toStatus] !== undefined
  );
  const completedAt = statusHistoryTime(order, "COMPLETED");
  const noShowAt = statusHistoryTime(order, "NO_SHOW");

  return (
    <div className="flex flex-1 flex-col">
      {/*
       * docs/06 SC-110은 상단 안내 띠의 정확한 문구를 못박지 않는다. 06이 침묵하는
       * 부분이라 `wireframes/sc-110-admin-order-detail.html`의 문구(완료·노쇼 두 상태에만
       * 있는 `.band`)를 그대로 채택했다 — 최종 보고에 남긴다.
       */}
      {order.status === "COMPLETED" && completedAt ? (
        <div className="bg-border px-4 py-3">
          <p className="font-body text-text-weak">{`${formatKstTime(completedAt)}에 픽업 완료된 주문이에요`}</p>
        </div>
      ) : null}
      {order.status === "NO_SHOW" && noShowAt ? (
        <div className="bg-danger-weak px-4 py-3">
          <p className="font-body text-danger">{`${formatKstTime(noShowAt)}에 노쇼로 전환된 주문이에요`}</p>
        </div>
      ) : null}

      <div className="flex flex-1 flex-col gap-3 p-4">
        <div className="rounded-lg border border-border bg-surface p-4 text-center shadow-[var(--shadow-card)]">
          <OrderStatusBadge status={order.status} audience="admin" />
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
          <h2 className="font-heading mb-2">고객</h2>
          <dl className="flex flex-col gap-2">
            <Row label="이름" value={order.customer.name} />
            <Row label="휴대폰" value={order.customer.phone} />
          </dl>
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

        {order.paymentAttempts.length > 0 ? (
          <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
            <h2 className="font-heading mb-2">결제 시도 이력</h2>
            <dl className="flex flex-col gap-2">
              {order.paymentAttempts.map((attempt) => (
                <Row
                  key={attempt.attemptNo}
                  label={`${attempt.attemptNo}회 · ${PAYMENT_ATTEMPT_LABELS[attempt.status]}`}
                  value={formatKstTime(attempt.requestedAt)}
                />
              ))}
            </dl>
          </div>
        ) : null}

        {historyEntries.length > 0 ? (
          <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
            <h2 className="font-heading mb-2">상태 변경 이력</h2>
            <dl className="flex flex-col gap-2">
              {historyEntries.map((entry) => (
                <Row
                  key={`${entry.toStatus}-${entry.occurredAt}`}
                  label={HISTORY_LABELS[entry.toStatus] as string}
                  value={formatKstDateTime(entry.occurredAt)}
                />
              ))}
            </dl>
          </div>
        ) : null}

        <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
          <Row label="주문 번호" value={order.orderNo} />
        </div>
      </div>

      {order.availableActions.length > 0 ? (
        <div className="sticky bottom-0 flex flex-wrap gap-2 border-t border-border bg-surface p-4">
          {order.availableActions.includes("READY") ? (
            <Button
              variant="secondary"
              className="flex-1"
              onClick={() => void handleReady()}
              disabled={actionSubmitting !== null}
            >
              {actionSubmitting === "READY" ? "처리하는 중이에요" : "준비 완료"}
            </Button>
          ) : null}
          {order.availableActions.includes("COMPLETE") ? (
            <Button
              className="flex-1"
              onClick={() => void handleComplete()}
              disabled={actionSubmitting !== null}
            >
              {actionSubmitting === "COMPLETE" ? "처리하는 중이에요" : "픽업 완료"}
            </Button>
          ) : null}
          {order.availableActions.includes("CANCEL") ? (
            <Button
              variant="danger"
              className="w-full"
              onClick={openCancelSheet}
              disabled={actionSubmitting !== null}
            >
              관리자 취소
            </Button>
          ) : null}
        </div>
      ) : (
        <div className="sticky bottom-0 border-t border-border bg-surface p-4">
          <p className="font-caption text-center text-text-weak">더 이상 바꿀 수 있는 상태가 없어요</p>
        </div>
      )}

      <BottomSheet open={cancelSheetOpen} onClose={closeCancelSheet}>
        <div className="flex flex-col gap-3">
          <h3 className="font-heading">주문을 취소할까요?</h3>
          <div>
            <label htmlFor="cancel-reason" className="font-caption mb-1 block text-text-weak">
              취소 사유 (필수)
            </label>
            <textarea
              id="cancel-reason"
              value={cancelReason}
              onChange={(event) => setCancelReason(event.target.value)}
              disabled={cancelSubmitting}
              aria-invalid={cancelFieldError ? true : undefined}
              className={`font-body h-[72px] w-full rounded-md border bg-surface px-3 py-2 text-text outline-none transition-colors disabled:bg-bg disabled:text-text-weak ${
                cancelFieldError ? "border-danger" : "border-border focus:border-brand"
              }`}
            />
            {cancelFieldError ? (
              <p className="font-caption mt-1 text-danger">{cancelFieldError}</p>
            ) : (
              <p className="font-caption mt-1 text-text-weak">
                {hasClosedItem(order)
                  ? "마감 시각이 지나 재고로 되돌리지 않고 폐기로 기록해요"
                  : `취소하면 수량 ${totalQuantityOf(order)}개가 판매 가능 재고로 돌아가요`}
              </p>
            )}
          </div>
          <Button
            variant="danger"
            onClick={() => void submitCancel()}
            disabled={cancelSubmitting}
          >
            {cancelSubmitting ? "취소 처리하는 중이에요" : "취소 처리하기"}
          </Button>
          <Button variant="secondary" onClick={closeCancelSheet} disabled={cancelSubmitting}>
            닫기
          </Button>
        </div>
      </BottomSheet>

      <BottomSheet open={actionErrorSheet !== null} onClose={() => setActionErrorSheet(null)}>
        {actionErrorSheet ? (
          <div className="flex flex-col gap-3">
            <p className="font-body text-text">{actionErrorSheet}</p>
            <Button onClick={() => setActionErrorSheet(null)}>닫기</Button>
          </div>
        ) : null}
      </BottomSheet>

      <BottomSheet open={cancelErrorSheet !== null} onClose={() => setCancelErrorSheet(null)}>
        {cancelErrorSheet ? (
          <div className="flex flex-col gap-3">
            <p className="font-body text-text">{cancelErrorSheet}</p>
            <Button onClick={() => setCancelErrorSheet(null)}>닫기</Button>
          </div>
        ) : null}
      </BottomSheet>
    </div>
  );
}
