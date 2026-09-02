"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { completeOrderPickup, fetchOrderByPickupNumber } from "@/features/admin-order/api";
import type { AdminOrderDetailResponse } from "@/features/admin-order/types";
import type { OrderStatusValue } from "@/features/order/types";
import { OrderStatusBadge } from "@/components/ui/OrderStatusBadge";
import { Button } from "@/components/ui/Button";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Skeleton } from "@/components/ui/Skeleton";
import { TextField } from "@/components/ui/TextField";
import { ApiError } from "@/lib/api-client";
import { formatKstDateTime, formatKstTime, formatWon } from "@/lib/format";

type Phase = "input" | "loading" | "result" | "empty" | "error";

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between border-b border-border pb-2 last:border-0 last:pb-0">
      <dt className="font-caption text-text-weak">{label}</dt>
      <dd className="font-body">{value}</dd>
    </div>
  );
}

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

function totalQuantityOf(order: AdminOrderDetailResponse): number {
  return order.items.reduce((sum, item) => sum + item.quantity, 0);
}

/**
 * SC-109 · 픽업 번호 조회 (docs/06-screen-list.md §4).
 * 관리자 전용 화면이라 `app/admin/layout.tsx`의 AdminGate가 인증을 이미 보장한다.
 * Client Component에서 액션(조회·완료 처리) 시점에 `authScope: "admin"`으로 직접
 * 호출한다(ARCHITECTURE.md 데이터 페칭 규칙).
 *
 * 판단: docs/06 "기본(조회 전)"은 "큰 숫자 입력 키패드"를 요구하지만, 실제 키패드는
 * `docs/09-ui-design-brief.md` §2에 정의된 공통 컴포넌트가 아니고 이 화면 하나만 쓴다.
 * 이미 있는 `TextField`(숫자 입력 + 라벨 + 오류 캡션)로 같은 기능(3자리 숫자 입력)을
 * 구현했다 — 새 전용 컴포넌트를 만드는 대신 기존 컴포넌트를 재사용했다.
 *
 * SC-102(관리자 홈)의 "픽업 번호 빠른 입력창"이 `?number=042`로 번호를 넘긴다. 번호를
 * 두 번 입력하게 하지 않으려고 진입 즉시 그 번호로 한 번 조회한다.
 */
export function PickupLookupView() {
  const searchParams = useSearchParams();
  const initialNumber = (searchParams.get("number") ?? "").replace(/\D/g, "").slice(0, 3);

  const [value, setValue] = useState(initialNumber);
  const [inputError, setInputError] = useState<string | null>(null);
  const [phase, setPhase] = useState<Phase>("input");
  const [order, setOrder] = useState<AdminOrderDetailResponse | null>(null);
  const [queriedNumber, setQueriedNumber] = useState("");

  const [completing, setCompleting] = useState(false);
  const [completeError, setCompleteError] = useState<string | null>(null);

  function runLookup(pickupNumber: string) {
    setPhase("loading");
    fetchOrderByPickupNumber(pickupNumber)
      .then((data) => {
        setOrder(data);
        setPhase("result");
      })
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.code === "NOT_FOUND") {
          setPhase("empty");
        } else {
          setPhase("error");
        }
      });
  }

  // 넘겨받은 번호로 한 번만 자동 조회한다. effect 본문에서 setState를 동기 호출하지
  // 않도록 조회는 runLookup 안의 .then() 콜백에서만 상태를 바꾼다
  // (react-hooks/set-state-in-effect).
  const autoLookupDoneRef = useRef(false);
  useEffect(() => {
    if (autoLookupDoneRef.current || initialNumber.length === 0) return;
    autoLookupDoneRef.current = true;
    const padded = initialNumber.padStart(3, "0");
    setQueriedNumber(padded);
    runLookup(padded);
  }, [initialNumber]);

  function handleSubmit() {
    const trimmed = value.trim();
    if (!/^\d{1,3}$/.test(trimmed) || Number(trimmed) < 1) {
      setInputError("1~999 사이의 픽업 번호를 입력해주세요");
      return;
    }
    setInputError(null);
    const padded = trimmed.padStart(3, "0");
    setQueriedNumber(padded);
    runLookup(padded);
  }

  function handleRetry() {
    runLookup(queriedNumber);
  }

  function handleReset() {
    setValue("");
    setInputError(null);
    setPhase("input");
    setOrder(null);
  }

  async function handleComplete() {
    if (!order) return;
    setCompleting(true);
    setCompleteError(null);
    try {
      await completeOrderPickup(order.orderId);
    } catch (error) {
      if (error instanceof ApiError && error.code === "INVALID_ORDER_STATUS") {
        const currentStatus =
          typeof error.details?.currentStatus === "string" ? error.details.currentStatus : undefined;
        setCompleteError(
          currentStatus === "NO_SHOW"
            ? "노쇼로 전환된 주문은 완료 처리할 수 없어요"
            : "이미 완료 처리된 주문이에요"
        );
      } else {
        setCompleteError("처리하지 못했어요");
      }
    }
    try {
      const refreshed = await fetchOrderByPickupNumber(queriedNumber);
      setOrder(refreshed);
    } catch {
      // 새로고침 실패는 무시한다 — 화면엔 직전 데이터가 그대로 남는다.
    }
    setCompleting(false);
  }

  return (
    <div className="flex flex-col gap-3 p-4">
      <h1 className="font-heading">픽업 번호 조회</h1>

      <div>
        <TextField
          id="pickup-number"
          label="픽업 번호 3자리"
          inputMode="numeric"
          maxLength={3}
          value={value}
          onChange={(event) => setValue(event.target.value.replace(/\D/g, "").slice(0, 3))}
          error={inputError ?? undefined}
          disabled={phase === "loading"}
        />
        <p className="font-caption -mt-3 text-text-weak">
          오늘 영업일 기준으로 찾아요. 다른 영업일의 같은 번호는 함께 조회하지 않아요.
        </p>
      </div>

      {/*
       * 조회 결과가 없거나(empty) 통신에 실패한(error) 뒤에도 번호를 고쳐 다시 조회할 수
       * 있어야 한다 — 빈 상태 안내도 "번호를 다시 확인하라"고 말한다. 결과 카드가 떠 있는
       * 동안에는 카드 안 "다른 번호 조회"가 입력 화면으로 되돌린다.
       */}
      {phase !== "loading" && phase !== "result" ? (
        <Button onClick={handleSubmit} disabled={value.length === 0}>
          조회
        </Button>
      ) : null}

      {phase === "loading" ? (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-6 w-32" />
          <Skeleton className="h-24 w-full" />
          <p className="font-caption text-center text-text-weak">주문을 찾는 중이에요</p>
        </div>
      ) : null}

      {phase === "empty" ? (
        <EmptyState
          message={`픽업 번호 ${queriedNumber}에 해당하는 주문이 없어요`}
          reason="번호를 다시 확인하거나 주문 목록에서 찾아보세요"
          action={
            <Link
              href="/admin/orders"
              className="font-body flex h-[52px] w-auto items-center justify-center rounded-md border border-border px-6 text-text"
            >
              주문 목록으로
            </Link>
          }
        />
      ) : null}

      {phase === "error" ? (
        <ErrorState message="불러오지 못했어요" onRetry={handleRetry} />
      ) : null}

      {phase === "result" && order ? (
        <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
          <div className="mb-2 flex items-center justify-between gap-2">
            <h2 className="font-heading">{`픽업 번호 ${order.pickupNumber ?? queriedNumber}`}</h2>
            <OrderStatusBadge status={order.status} audience="admin" />
          </div>
          <dl className="flex flex-col gap-2">
            <Row label="고객 이름" value={order.customer.name} />
            <Row label="픽업 시간대" value={pickupTimeText(order) ?? "-"} />
            <Row
              label="품목 수"
              value={`${order.items.length}건 (${totalQuantityOf(order)}개)`}
            />
            <Row label="결제 금액" value={formatWon(order.totalAmount)} />
          </dl>
          <Link
            href={`/admin/orders/${order.orderId}`}
            className="font-body mt-3 flex h-12 items-center justify-center rounded-md border border-border text-text"
          >
            주문 상세 열기
          </Link>

          {order.status === "COMPLETED" ? (
            <div className="mt-3">
              <Button
                disabled
                disabledReason={`이미 ${
                  statusHistoryTime(order, "COMPLETED")
                    ? formatKstTime(statusHistoryTime(order, "COMPLETED") as string)
                    : ""
                }에 픽업 완료된 주문이에요`}
              >
                픽업 완료 처리
              </Button>
            </div>
          ) : order.status === "NO_SHOW" ? (
            <div className="mt-3">
              <Button disabled disabledReason="노쇼로 처리돼 수령할 수 없어요">
                픽업 완료 처리
              </Button>
            </div>
          ) : order.availableActions.includes("COMPLETE") ? (
            <Button className="mt-3" onClick={() => void handleComplete()} disabled={completing}>
              {completing ? "처리하는 중이에요" : "픽업 완료 처리"}
            </Button>
          ) : (
            <div className="mt-3">
              <Button disabled disabledReason="지금 상태에서는 할 수 없는 조작이에요">
                픽업 완료 처리
              </Button>
            </div>
          )}

          <button
            type="button"
            onClick={handleReset}
            className="font-caption mt-3 flex h-11 w-full items-center justify-center text-brand"
          >
            다른 번호 조회
          </button>
        </div>
      ) : null}

      <BottomSheet open={completeError !== null} onClose={() => setCompleteError(null)}>
        {completeError ? (
          <div className="flex flex-col gap-3">
            <p className="font-body text-text">{completeError}</p>
            <Button onClick={() => setCompleteError(null)}>닫기</Button>
          </div>
        ) : null}
      </BottomSheet>
    </div>
  );
}
