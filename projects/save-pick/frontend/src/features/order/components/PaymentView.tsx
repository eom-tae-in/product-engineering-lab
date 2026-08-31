"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth/customer-auth";
import { abandonOrder, fetchOrderDetail, fetchOrderHold, submitPayment } from "@/features/order/api";
import type { OrderDetailResponse } from "@/features/order/types";
import { HoldExpiredSheet } from "./HoldExpiredSheet";
import { HoldTimerBar } from "@/components/ui/HoldTimerBar";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { ApiError } from "@/lib/api-client";
import { formatKstTime, formatMmSs, formatWon } from "@/lib/format";

type FailureSheetState =
  | {
      kind: "retryable";
      paymentAttemptRemaining: number;
      holdRemainingSeconds: number;
      timeout: boolean;
    }
  | { kind: "amountMismatch" }
  | { kind: "slotClosed" }
  | { kind: "slotFull" }
  | { kind: "systemError" }
  | null;

const GENERIC_ERROR_MESSAGE = "일시적인 오류로 처리하지 못했어요. 잠시 뒤 다시 시도해주세요.";

/**
 * SC-007 · 결제 확인 및 결과 (docs/06-screen-list.md §3).
 * 로그인 필수. 마운트 시 주문 상세(API-024)·선점 잔여 시간(API-018)을 다시 조회한다.
 * 아직 픽업 시간대가 지정되지 않은 상태로 진입하면(진입 조건 미충족) SC-006으로
 * 돌려보낸다.
 */
export function PaymentView() {
  const auth = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const orderIdParam = searchParams.get("orderId");
  const orderId = orderIdParam ? Number(orderIdParam) : NaN;

  const [order, setOrder] = useState<OrderDetailResponse | null>(null);
  const [holdExpiresAt, setHoldExpiresAt] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadErrorOpen, setLoadErrorOpen] = useState(false);
  const [loadErrorMessage, setLoadErrorMessage] = useState(GENERIC_ERROR_MESSAGE);
  const [expired, setExpired] = useState(false);
  const [terminalFailed, setTerminalFailed] = useState(false);
  const [paying, setPaying] = useState(false);
  const [failureSheet, setFailureSheet] = useState<FailureSheetState>(null);
  const [abandoning, setAbandoning] = useState(false);

  const runLoad = useCallback(() => {
    return Promise.all([fetchOrderDetail(orderId), fetchOrderHold(orderId)])
      .then(([orderDetail, hold]) => {
        if (hold.status === "EXPIRED") {
          setExpired(true);
          return;
        }
        if (!orderDetail.pickupStartAt || !orderDetail.pickupEndAt) {
          router.replace(`/orders/new/pickup?orderId=${orderId}`);
          return;
        }
        setOrder(orderDetail);
        setHoldExpiresAt(hold.holdExpiresAt);
        setLoadErrorOpen(false);
      })
      .catch((error: unknown) => {
        setLoadErrorMessage(error instanceof ApiError ? error.defaultMessage : GENERIC_ERROR_MESSAGE);
        setLoadErrorOpen(true);
      })
      .finally(() => setLoading(false));
  }, [orderId, router]);

  useEffect(() => {
    if (auth.status === "guest") {
      router.replace("/login");
    }
  }, [auth.status, router]);

  useEffect(() => {
    if (auth.status === "authenticated" && Number.isFinite(orderId)) {
      void runLoad();
    } else if (auth.status === "authenticated") {
      router.replace("/cart");
    }
    // router는 next/navigation이 안정적인 참조를 보장하지 않는 mock 환경(테스트)에서
    // 매 렌더 재실행을 유발할 수 있어 의도적으로 의존성에서 뺀다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth.status, orderId, runLoad]);

  async function handlePay() {
    if (!order || paying) return;
    setPaying(true);
    setFailureSheet(null);
    try {
      const idempotencyKey = crypto.randomUUID();
      const response = await submitPayment(orderId, order.totalAmount, idempotencyKey);

      if (response.result === "SUCCEEDED") {
        router.replace(`/orders/${response.orderId}?justConfirmed=1`);
        return;
      }

      if (response.status === "FAILED") {
        setTerminalFailed(true);
        return;
      }

      if (response.holdExpiresAt) {
        setHoldExpiresAt(response.holdExpiresAt);
      }
      setFailureSheet({
        kind: "retryable",
        paymentAttemptRemaining: response.paymentAttemptRemaining,
        holdRemainingSeconds: response.holdRemainingSeconds ?? 0,
        timeout: response.failureReason === "TIMEOUT",
      });
    } catch (error) {
      if (error instanceof ApiError && error.code === "HOLD_EXPIRED") {
        setExpired(true);
      } else if (error instanceof ApiError && error.code === "AMOUNT_MISMATCH") {
        setFailureSheet({ kind: "amountMismatch" });
      } else if (error instanceof ApiError && error.code === "SLOT_CLOSED") {
        setFailureSheet({ kind: "slotClosed" });
      } else if (error instanceof ApiError && error.code === "SLOT_FULL") {
        setFailureSheet({ kind: "slotFull" });
      } else {
        setFailureSheet({ kind: "systemError" });
      }
    } finally {
      setPaying(false);
    }
  }

  async function handleAbandon() {
    setAbandoning(true);
    try {
      await abandonOrder(orderId);
    } catch {
      // 이미 유효하지 않은 주문서이거나 통신 오류여도 장바구니로 보낸다.
    } finally {
      setAbandoning(false);
      router.push("/cart");
    }
  }

  if (auth.status !== "authenticated" || loading) {
    return (
      <div className="flex flex-1 flex-col gap-3 p-4">
        <Skeleton className="h-11 w-full" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
    );
  }

  if (terminalFailed) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center gap-3 p-4 text-center">
        <h2 className="font-heading">주문이 종료됐어요</h2>
        <p className="font-body text-text-weak">선점한 수량은 다시 판매 가능해졌어요</p>
        <Button className="w-auto px-6" onClick={() => router.push("/cart")}>
          장바구니에서 다시 주문하기
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-1 flex-col">
      <HoldExpiredSheet open={expired} />

      {!expired && holdExpiresAt ? (
        <HoldTimerBar holdExpiresAt={holdExpiresAt} onExpire={() => setExpired(true)} />
      ) : null}

      {!expired && order ? (
        <div className="flex flex-1 flex-col gap-4 p-4">
          <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
            <p className="font-body text-text">
              {`픽업 ${formatKstTime(order.pickupStartAt as string)}~${formatKstTime(
                order.pickupEndAt as string
              )}`}
            </p>
          </div>

          <div className="flex flex-col gap-1 rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
            {order.items.map((item) => (
              <p key={item.productId} className="font-body text-text">
                {`${item.name} ×${item.quantity}`}
              </p>
            ))}
          </div>

          <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
            <div className="flex justify-between">
              <span className="font-caption text-text-weak">결제 확정 금액</span>
              <span className="font-price">{formatWon(order.totalAmount)}</span>
            </div>
          </div>

          <p className="font-caption text-text-weak">
            가상 결제입니다. 실제 금전 거래는 일어나지 않아요
          </p>

          <div className="flex flex-col gap-1">
            <Button
              variant="text"
              className="w-auto self-start px-0"
              onClick={() => router.push(`/orders/new/pickup?orderId=${orderId}`)}
            >
              시간대 다시 고르기
            </Button>
            <Button
              variant="text"
              className="w-auto self-start px-0"
              onClick={() => void handleAbandon()}
              disabled={abandoning}
            >
              {abandoning ? "포기하는 중이에요" : "주문서 포기"}
            </Button>
          </div>
        </div>
      ) : null}

      {!expired && order ? (
        <div className="sticky bottom-0 flex items-center gap-3 border-t border-border bg-surface p-4">
          <Button className="w-auto px-6" disabled={paying} onClick={() => void handlePay()}>
            {paying ? "결제 결과를 확인하는 중이에요" : `${formatWon(order.totalAmount)} 결제하기`}
          </Button>
        </div>
      ) : null}

      <BottomSheet open={loadErrorOpen} dismissible={order !== null}>
        <div className="flex flex-col gap-3">
          <p className="font-body text-text">{loadErrorMessage}</p>
          <Button
            onClick={() => {
              setLoading(true);
              void runLoad();
            }}
          >
            다시 시도
          </Button>
        </div>
      </BottomSheet>

      <BottomSheet open={failureSheet !== null} onClose={() => setFailureSheet(null)}>
        {failureSheet?.kind === "retryable" ? (
          <div className="flex flex-col gap-3">
            <p className="font-body text-text">결제가 완료되지 않았어요</p>
            <p
              className={`font-caption ${
                failureSheet.paymentAttemptRemaining <= 1 ? "text-warning" : "text-text-weak"
              }`}
            >
              {`선점은 ${formatMmSs(failureSheet.holdRemainingSeconds)} 남았고, ${
                failureSheet.paymentAttemptRemaining
              }번 더 시도할 수 있어요`}
            </p>
            {failureSheet.timeout ? (
              <p className="font-caption text-text-weak">응답을 받지 못해 실패로 처리했어요</p>
            ) : null}
            <Button onClick={() => void handlePay()} disabled={paying}>
              다시 결제하기
            </Button>
          </div>
        ) : failureSheet?.kind === "amountMismatch" ? (
          <div className="flex flex-col gap-3">
            <p className="font-body text-text">
              주문 금액이 달라졌어요. 주문서를 다시 확인해주세요
            </p>
            <Button onClick={() => router.push(`/orders/new?orderId=${orderId}`)}>
              주문서로 이동
            </Button>
          </div>
        ) : failureSheet?.kind === "slotClosed" ? (
          <div className="flex flex-col gap-3">
            <p className="font-body text-text">선택한 시간대의 예약이 방금 마감됐어요</p>
            <Button onClick={() => router.push(`/orders/new/pickup?orderId=${orderId}`)}>
              다른 시간대 고르기
            </Button>
          </div>
        ) : failureSheet?.kind === "slotFull" ? (
          <div className="flex flex-col gap-3">
            <p className="font-body text-text">선택한 시간대의 정원이 찼어요</p>
            <Button onClick={() => router.push(`/orders/new/pickup?orderId=${orderId}`)}>
              다른 시간대 고르기
            </Button>
          </div>
        ) : failureSheet?.kind === "systemError" ? (
          <div className="flex flex-col gap-3">
            <p className="font-body text-text">
              일시적인 오류로 결제를 처리하지 못했어요. 잠시 뒤 다시 시도해주세요
            </p>
            <Button onClick={() => void handlePay()}>다시 시도</Button>
          </div>
        ) : null}
      </BottomSheet>
    </div>
  );
}
