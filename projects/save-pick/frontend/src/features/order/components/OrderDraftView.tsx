"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth/customer-auth";
import { fetchMe } from "@/features/account/api";
import { abandonOrder, fetchOrderDetail, fetchOrderHold } from "@/features/order/api";
import type { OrderDetailResponse } from "@/features/order/types";
import { HoldExpiredSheet } from "./HoldExpiredSheet";
import { HoldTimerBar } from "@/components/ui/HoldTimerBar";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { formatWon } from "@/lib/format";

interface PickupContact {
  name: string;
  phone: string;
}

/**
 * SC-005 · 주문서 작성 (docs/06-screen-list.md §3).
 * 로그인 필수. 마운트 시 주문 상세(API-024)·선점 잔여 시간(API-018)·픽업 연락처
 * (API-005, 회원 이름·휴대폰)를 다시 조회해 새로고침에도 견고하게 만든다.
 */
export function OrderDraftView() {
  const auth = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const orderIdParam = searchParams.get("orderId");
  const orderId = orderIdParam ? Number(orderIdParam) : NaN;

  const [order, setOrder] = useState<OrderDetailResponse | null>(null);
  const [holdExpiresAt, setHoldExpiresAt] = useState<string | null>(null);
  const [contact, setContact] = useState<PickupContact | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadErrorOpen, setLoadErrorOpen] = useState(false);
  const [expired, setExpired] = useState(false);
  const [expiringSoon, setExpiringSoon] = useState(false);
  const [abandoning, setAbandoning] = useState(false);

  const runLoad = useCallback(() => {
    return Promise.all([fetchOrderDetail(orderId), fetchOrderHold(orderId), fetchMe()])
      .then(([orderDetail, hold, me]) => {
        if (hold.status === "EXPIRED") {
          setExpired(true);
          return;
        }
        setOrder(orderDetail);
        setHoldExpiresAt(hold.holdExpiresAt);
        setContact({ name: me.name, phone: me.phone });
        setLoadErrorOpen(false);
      })
      .catch(() => {
        setLoadErrorOpen(true);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [orderId]);

  useEffect(() => {
    if (auth.status === "guest") {
      router.replace("/login");
    }
  }, [auth.status, router]);

  useEffect(() => {
    if (auth.status === "authenticated" && Number.isFinite(orderId)) {
      void runLoad();
    } else if (auth.status === "authenticated") {
      // orderId가 없거나 숫자가 아니면 이 화면을 유지할 근거가 없다.
      router.replace("/cart");
    }
    // router는 next/navigation이 안정적인 참조를 보장하지 않는 mock 환경(테스트)에서
    // 매 렌더 재실행을 유발할 수 있어 의도적으로 의존성에서 뺀다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth.status, orderId, runLoad]);

  async function handleAbandon() {
    setAbandoning(true);
    try {
      await abandonOrder(orderId);
    } catch {
      // 이미 유효하지 않은 주문서이거나 통신 오류여도 이 주문서로는 되돌아올 수
      // 없으므로 장바구니로 보낸다.
    } finally {
      setAbandoning(false);
      router.push("/cart");
    }
  }

  if (auth.status !== "authenticated" || loading) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center gap-3 p-4 text-center">
        <Skeleton className="h-11 w-full" />
        <p className="font-body text-text">재고를 확보하는 중이에요</p>
      </div>
    );
  }

  return (
    <div className="flex flex-1 flex-col">
      <HoldExpiredSheet open={expired} />

      {!expired && holdExpiresAt ? (
        <HoldTimerBar
          holdExpiresAt={holdExpiresAt}
          onExpire={() => setExpired(true)}
          onTick={(remainingSeconds) => setExpiringSoon(remainingSeconds > 0 && remainingSeconds <= 60)}
        />
      ) : null}

      {expiringSoon ? (
        <div className="bg-warning-weak px-4 py-3">
          <p className="font-caption text-warning">
            1분 뒤 선점이 풀려요. 시간은 연장되지 않아요
          </p>
        </div>
      ) : null}

      {order ? (
        <div className="flex flex-1 flex-col gap-4 p-4">
          <div className="flex flex-col gap-2">
            <h2 className="font-heading">{`주문 상품 ${order.items.length}건`}</h2>
            <div className="flex flex-col gap-2 rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
              {order.items.map((item) => (
                <div key={item.productId} className="flex items-center justify-between">
                  <span className="font-body text-text">
                    {`${item.name} ×${item.quantity}`}
                  </span>
                  <span className="font-body font-medium">{formatWon(item.lineAmount)}</span>
                </div>
              ))}
            </div>
          </div>

          <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
            <div className="flex justify-between">
              <span className="font-caption text-text-weak">결제 예정 금액</span>
              <span className="font-price">{formatWon(order.totalAmount)}</span>
            </div>
            <p className="font-caption mt-1 text-text-weak">
              주문서 금액은 지금 시점의 할인가로 고정돼요
            </p>
          </div>

          {contact ? (
            <div className="flex flex-col gap-1">
              <h2 className="font-heading">픽업 연락처</h2>
              <p className="font-body text-text">{`${contact.name} · ${contact.phone}`}</p>
            </div>
          ) : null}

          <Button
            variant="text"
            className="w-auto self-start px-0"
            onClick={() => void handleAbandon()}
            disabled={abandoning}
          >
            {abandoning ? "포기하는 중이에요" : "주문서 포기"}
          </Button>
        </div>
      ) : null}

      {order ? (
        <div className="sticky bottom-0 flex items-center gap-3 border-t border-border bg-surface p-4">
          <div className="flex-1">
            <p className="font-price">{formatWon(order.totalAmount)}</p>
          </div>
          <Button
            className="w-auto px-6"
            onClick={() => router.push(`/orders/new/pickup?orderId=${orderId}`)}
          >
            픽업 시간 고르기
          </Button>
        </div>
      ) : null}

      <BottomSheet open={loadErrorOpen} dismissible={order !== null}>
        <div className="flex flex-col gap-3">
          <p className="font-body text-text">주문서를 불러오지 못했어요</p>
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
    </div>
  );
}
