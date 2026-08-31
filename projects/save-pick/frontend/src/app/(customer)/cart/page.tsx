"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/customer-auth";
import {
  createOrder,
  fetchCart,
  removeCartItem,
  removeUnavailableCartItems,
  updateCartItemQuantity,
} from "@/features/cart/api";
import { fetchNoShowStatus } from "@/features/account/api";
import type { CartItem, CartResponse } from "@/features/cart/types";
import { ApiError } from "@/lib/api-client";
import { formatKstDateTime, formatWon } from "@/lib/format";
import { Badge } from "@/components/ui/Badge";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { Button } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { QuantityStepper } from "@/components/ui/QuantityStepper";
import { Skeleton } from "@/components/ui/Skeleton";

/** 장바구니 품목 10개까지만 담을 수 있다(BR-009). */
const CART_ITEM_LIMIT = 10;

const GENERIC_ERROR_MESSAGE = "일시적인 오류로 처리하지 못했어요. 잠시 뒤 다시 시도해주세요.";

type OrderSheetState =
  | { kind: "outOfStock"; shortages: { name: string; requested: number; available: number }[] }
  | { kind: "systemError" }
  | { kind: "restricted"; message: string }
  | { kind: "pendingOrder"; orderId: number | null }
  | { kind: "genericError"; message: string };

function isFullyOutOfStock(item: CartItem): boolean {
  return item.unavailableReason === "OUT_OF_STOCK" && item.availableQuantity === 0;
}

function isShortage(item: CartItem): boolean {
  return (
    item.unavailableReason === "OUT_OF_STOCK" && item.availableQuantity > 0 && item.shortage > 0
  );
}

/** 재고 소진·마감·판매중지 품목은 수량을 조절해도 해결되지 않으므로 조절기를 잠근다. */
function isStepperDisabled(item: CartItem): boolean {
  if (isShortage(item)) return false;
  return !item.purchasable;
}

/** 잔여 수량까지만 늘릴 수 있게 상한을 둔다(현재 담긴 수량보다 낮아지지 않게 한다). */
function stepperMax(item: CartItem): number {
  return Math.max(item.availableQuantity, item.quantity);
}

function rowCaptions(item: CartItem): string[] {
  const captions: string[] = [];
  if (isShortage(item)) {
    captions.push(`남은 수량 ${item.availableQuantity}개 · ${item.shortage}개 부족`);
  }
  if (item.priceChanged) {
    captions.push(
      `할인 구간이 바뀌어 가격이 ${formatWon(item.addedPrice)} → ${formatWon(
        item.currentPrice
      )}으로 변경됐어요`
    );
  }
  return captions;
}

function rowBadge(item: CartItem) {
  if (isFullyOutOfStock(item)) return <Badge tone="soldout">품절</Badge>;
  if (item.unavailableReason === "PRODUCT_CLOSED") return <Badge tone="closed">판매 종료</Badge>;
  if (item.unavailableReason === "PRODUCT_NOT_ON_SALE") {
    return <Badge tone="closed">구매 불가</Badge>;
  }
  return null;
}

function toShortageEntries(
  details: Record<string, unknown> | undefined,
  cartItems: CartItem[]
): { name: string; requested: number; available: number }[] {
  const raw = details?.shortages;
  if (!Array.isArray(raw)) return [];
  return raw.map((entry) => {
    const record = (entry ?? {}) as Record<string, unknown>;
    const productId = typeof record.productId === "number" ? record.productId : null;
    const matched = cartItems.find((item) => item.productId === productId);
    return {
      name: matched?.name ?? (productId !== null ? `상품 ${productId}` : "상품"),
      requested: typeof record.requested === "number" ? record.requested : 0,
      available: typeof record.available === "number" ? record.available : 0,
    };
  });
}

/**
 * SC-004 · 장바구니 (docs/06-screen-list.md §3).
 * 담기·수정은 비로그인도 가능해 게스트 토큰으로 조회한다(로그인 상태면 액세스
 * 토큰이 우선 적용된다). 게스트 토큰·액세스 토큰 둘 다 클라이언트에만 있어
 * Client Component에서 마운트 시 직접 호출한다(ARCHITECTURE.md 데이터 페칭 규칙).
 */
export default function CartPage() {
  const auth = useAuth();
  const router = useRouter();

  const [cart, setCart] = useState<CartResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [pendingItemId, setPendingItemId] = useState<number | null>(null);
  const [rowErrors, setRowErrors] = useState<Record<number, string>>({});

  const [removingUnavailable, setRemovingUnavailable] = useState(false);
  const [bulkRemoveError, setBulkRemoveError] = useState<string | null>(null);

  const [creatingOrder, setCreatingOrder] = useState(false);
  const [orderSheet, setOrderSheet] = useState<OrderSheetState | null>(null);

  // effect 안에서 setState가 동기 실행되지 않도록 .then()/.catch() 콜백 안에서만
  // setState한다(react-hooks/set-state-in-effect, me/page.tsx의 runLoad와 동일 패턴).
  const runLoad = useCallback(() => {
    return fetchCart()
      .then((data) => {
        setCart(data);
        setLoadError(null);
      })
      .catch(() => {
        setLoadError("장바구니를 불러오지 못했어요");
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  // "다시 시도" 버튼 전용. 전체 화면을 로딩으로 되돌린 뒤 다시 불러온다. 수량
  // 변경 같은 일상적인 재검증(runLoad)은 화면을 스켈레톤으로 되돌리지 않는다.
  const loadData = useCallback(() => {
    setLoading(true);
    setLoadError(null);
    return runLoad();
  }, [runLoad]);

  useEffect(() => {
    runLoad();
  }, [runLoad]);

  async function handleQuantityChange(item: CartItem, nextQuantity: number) {
    setPendingItemId(item.cartItemId);
    setRowErrors((prev) => {
      const next = { ...prev };
      delete next[item.cartItemId];
      return next;
    });
    try {
      if (nextQuantity <= 0) {
        await removeCartItem(item.cartItemId);
      } else {
        await updateCartItemQuantity(item.cartItemId, nextQuantity);
      }
      await runLoad();
    } catch (error) {
      setRowErrors((prev) => ({
        ...prev,
        [item.cartItemId]: error instanceof ApiError ? error.defaultMessage : GENERIC_ERROR_MESSAGE,
      }));
    } finally {
      setPendingItemId(null);
    }
  }

  async function handleRemoveItem(cartItemId: number) {
    setPendingItemId(cartItemId);
    setRowErrors((prev) => {
      const next = { ...prev };
      delete next[cartItemId];
      return next;
    });
    try {
      await removeCartItem(cartItemId);
      await runLoad();
    } catch (error) {
      setRowErrors((prev) => ({
        ...prev,
        [cartItemId]: error instanceof ApiError ? error.defaultMessage : GENERIC_ERROR_MESSAGE,
      }));
    } finally {
      setPendingItemId(null);
    }
  }

  async function handleRemoveUnavailable() {
    setRemovingUnavailable(true);
    setBulkRemoveError(null);
    try {
      await removeUnavailableCartItems();
      await runLoad();
    } catch (error) {
      setBulkRemoveError(error instanceof ApiError ? error.defaultMessage : GENERIC_ERROR_MESSAGE);
    } finally {
      setRemovingUnavailable(false);
    }
  }

  async function handleCreateOrder() {
    if (auth.status !== "authenticated") {
      router.push("/login");
      return;
    }
    if (!cart || !cart.orderable) return;

    setCreatingOrder(true);
    try {
      const purchasableIds = cart.items.filter((item) => item.purchasable).map((item) => item.cartItemId);
      const order = await createOrder(purchasableIds);
      router.push(`/orders/new?orderId=${order.orderId}`);
      return;
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.code === "OUT_OF_STOCK") {
          setOrderSheet({
            kind: "outOfStock",
            shortages: toShortageEntries(error.details, cart.items),
          });
        } else if (error.code === "INTERNAL_ERROR") {
          setOrderSheet({ kind: "systemError" });
        } else if (error.code === "ORDER_RESTRICTED") {
          const restrictedUntil =
            typeof error.details?.restrictedUntil === "string" ? error.details.restrictedUntil : null;
          let recentNoShowCount = 0;
          try {
            const status = await fetchNoShowStatus();
            recentNoShowCount = status.recentNoShowCount;
          } catch {
            // 노쇼 횟수를 못 가져와도 시트는 그대로 보여준다.
          }
          setOrderSheet({
            kind: "restricted",
            message: restrictedUntil
              ? `노쇼 ${recentNoShowCount}회 누적으로 ${formatKstDateTime(
                  restrictedUntil
                )}까지 주문할 수 없어요`
              : "노쇼 누적으로 지금은 새 주문을 만들 수 없어요",
          });
        } else if (error.code === "PENDING_ORDER_EXISTS") {
          const orderId = typeof error.details?.orderId === "number" ? error.details.orderId : null;
          setOrderSheet({ kind: "pendingOrder", orderId });
        } else {
          setOrderSheet({ kind: "genericError", message: error.defaultMessage });
        }
      } else {
        setOrderSheet({ kind: "genericError", message: GENERIC_ERROR_MESSAGE });
      }
    } finally {
      setCreatingOrder(false);
    }
  }

  if (loading) {
    return (
      <div className="flex flex-1 flex-col gap-3 p-4">
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
        <div className="mt-auto">
          <Button disabled>주문하기</Button>
        </div>
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="p-4">
        <ErrorState message={loadError} onRetry={loadData} />
      </div>
    );
  }

  if (!cart) {
    return null;
  }

  if (cart.items.length === 0) {
    return (
      <div className="p-4">
        <EmptyState
          message="장바구니가 비어 있어요"
          action={
            <Link
              href="/"
              className="font-body flex h-[52px] w-auto items-center justify-center rounded-md bg-brand px-6 font-medium text-on-brand"
            >
              마감 할인 상품 보러 가기
            </Link>
          }
        />
      </div>
    );
  }

  const unavailableItems = cart.items.filter((item) => !item.purchasable);
  const hasUnavailable = unavailableItems.length > 0;
  const atItemLimit = cart.items.length >= CART_ITEM_LIMIT;

  return (
    <div className="flex flex-1 flex-col">
      <div className="flex flex-1 flex-col gap-3 p-4">
        {hasUnavailable ? (
          <div className="rounded-md bg-warning-weak px-4 py-3">
            <p className="font-body text-warning">{`구매할 수 없는 품목 ${unavailableItems.length}건이 있어요`}</p>
          </div>
        ) : null}

        {atItemLimit ? (
          <div className="rounded-md bg-border px-4 py-3">
            <p className="font-body text-text-weak">
              장바구니에 품목을 10개까지 담았어요. 새 품목을 담으려면 먼저 정리해주세요
            </p>
          </div>
        ) : null}

        <div className="flex flex-col gap-3">
          {cart.items.map((item) => {
            const busy = pendingItemId === item.cartItemId;
            return (
              <div
                key={item.cartItemId}
                className={`rounded-lg border border-border p-4 shadow-[var(--shadow-card)] ${
                  isFullyOutOfStock(item) ? "bg-warning-weak" : "bg-surface"
                }`}
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <Link
                        href={`/products/${item.productId}`}
                        className="font-body truncate text-text"
                      >
                        {item.name}
                      </Link>
                      {rowBadge(item)}
                    </div>
                    <p className="font-price text-discount">{formatWon(item.currentPrice)}</p>
                    {rowCaptions(item).map((caption) => (
                      <p key={caption} className="font-caption mt-1 text-warning">
                        {caption}
                      </p>
                    ))}
                    {rowErrors[item.cartItemId] ? (
                      <p className="font-caption mt-1 text-danger">{rowErrors[item.cartItemId]}</p>
                    ) : null}
                  </div>
                  <span className="font-body flex-none font-medium">
                    {formatWon(item.lineAmount)}
                  </span>
                </div>

                <div className="mt-2 flex items-center justify-between">
                  <QuantityStepper
                    value={item.quantity}
                    min={0}
                    max={stepperMax(item)}
                    disabled={isStepperDisabled(item) || busy}
                    onChange={(next) => void handleQuantityChange(item, next)}
                    decreaseLabel={`${item.name} 수량 줄이기`}
                    increaseLabel={`${item.name} 수량 늘리기`}
                  />
                  <button
                    type="button"
                    onClick={() => void handleRemoveItem(item.cartItemId)}
                    disabled={busy}
                    className="font-body h-11 px-2 text-text-weak disabled:opacity-50"
                  >
                    삭제
                  </button>
                </div>
              </div>
            );
          })}
        </div>

        {hasUnavailable ? (
          <div className="flex flex-col gap-1">
            <Button
              variant="danger"
              onClick={() => void handleRemoveUnavailable()}
              disabled={removingUnavailable}
            >
              {removingUnavailable ? "정리하는 중이에요" : "구매 불가 품목 삭제"}
            </Button>
            {bulkRemoveError ? (
              <p className="font-caption text-danger">{bulkRemoveError}</p>
            ) : null}
          </div>
        ) : null}

        <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
          <div className="flex justify-between">
            <span className="font-caption text-text-weak">결제 예정 금액</span>
            <span className="font-price">{formatWon(cart.totalAmount)}</span>
          </div>
        </div>
      </div>

      <div className="sticky bottom-0 flex flex-col gap-2 border-t border-border bg-surface p-4">
        {!cart.orderable ? (
          <p className="font-caption text-warning">구매할 수 없는 품목을 정리하면 주문할 수 있어요</p>
        ) : null}
        <div className="flex items-center gap-3">
          <div className="flex-1">
            <p className="font-caption text-text-weak">결제 예정 금액</p>
            <p className="font-price">{formatWon(cart.totalAmount)}</p>
          </div>
          <Button
            className="w-auto px-6"
            disabled={!cart.orderable || creatingOrder}
            onClick={() => void handleCreateOrder()}
          >
            {creatingOrder ? "주문서를 만드는 중이에요" : "주문하기"}
          </Button>
        </div>
      </div>

      <BottomSheet
        open={orderSheet !== null}
        onClose={() => setOrderSheet(null)}
        dismissible={orderSheet?.kind !== "restricted"}
      >
        {orderSheet?.kind === "outOfStock" ? (
          <div className="flex flex-col gap-3">
            <h3 className="font-heading">방금 다른 고객이 먼저 담았어요</h3>
            <ul className="flex flex-col gap-1">
              {orderSheet.shortages.map((entry) => (
                <li key={entry.name} className="font-caption text-text-weak">
                  {`${entry.name} — 요청 ${entry.requested}개 / 남은 수량 ${entry.available}개`}
                </li>
              ))}
            </ul>
            <Button
              onClick={() => {
                setOrderSheet(null);
                void runLoad();
              }}
            >
              장바구니 새로고침
            </Button>
          </div>
        ) : orderSheet?.kind === "systemError" ? (
          <div className="flex flex-col gap-3">
            <p className="font-body text-text">
              일시적인 오류로 주문서를 만들지 못했어요. 잠시 뒤 다시 시도해주세요
            </p>
            <Button
              onClick={() => {
                setOrderSheet(null);
                void handleCreateOrder();
              }}
            >
              다시 시도
            </Button>
          </div>
        ) : orderSheet?.kind === "restricted" ? (
          <div className="flex flex-col gap-3">
            <p className="font-body text-text">{orderSheet.message}</p>
            <Button variant="secondary" onClick={() => setOrderSheet(null)}>
              닫기
            </Button>
          </div>
        ) : orderSheet?.kind === "pendingOrder" ? (
          <div className="flex flex-col gap-3">
            <h3 className="font-heading">진행 중인 주문서가 있어요</h3>
            <Button
              onClick={() => {
                const { orderId } = orderSheet;
                setOrderSheet(null);
                if (orderId) router.push(`/orders/new?orderId=${orderId}`);
              }}
            >
              주문서로 이동
            </Button>
          </div>
        ) : orderSheet?.kind === "genericError" ? (
          <div className="flex flex-col gap-3">
            <p className="font-body text-text">{orderSheet.message}</p>
            <Button variant="secondary" onClick={() => setOrderSheet(null)}>
              닫기
            </Button>
          </div>
        ) : null}
      </BottomSheet>
    </div>
  );
}
