"use client";

import { useState } from "react";
import Link from "next/link";
import { Badge } from "@/components/ui/Badge";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { Button } from "@/components/ui/Button";
import { QuantityStepper } from "@/components/ui/QuantityStepper";
import { addToCart } from "@/features/cart/api";
import { ApiError } from "@/lib/api-client";
import { formatKstTime, formatWon } from "@/lib/format";
import type { ProductDetailResponse } from "../types";

export interface ProductDetailViewProps {
  product: ProductDetailResponse;
}

type AddResult =
  | { kind: "success" }
  | { kind: "limitExceeded"; message: string }
  | { kind: "error"; message: string };

/**
 * SC-003 · 상품 상세 (docs/06-screen-list.md §3).
 * 서버에서 받은 초기 상품 정보를 보여주고, 수량 조절·장바구니 담기(클라이언트 상태
 * 변경)만 이 컴포넌트가 담당한다(ARCHITECTURE.md 데이터 페칭 규칙).
 */
export function ProductDetailView({ product }: ProductDetailViewProps) {
  const [quantity, setQuantity] = useState(1);
  const [adding, setAdding] = useState(false);
  const [result, setResult] = useState<AddResult | null>(null);

  const atMax = quantity >= product.maxOrderQuantity;
  const canOperate = product.purchasable;

  async function handleAddToCart() {
    setAdding(true);
    try {
      await addToCart(product.productId, quantity);
      setResult({ kind: "success" });
    } catch (error) {
      if (error instanceof ApiError && error.code === "CART_ITEM_LIMIT_EXCEEDED") {
        setResult({ kind: "limitExceeded", message: error.defaultMessage });
      } else if (error instanceof ApiError) {
        setResult({ kind: "error", message: error.defaultMessage });
      } else {
        setResult({
          kind: "error",
          message: "일시적인 오류로 처리하지 못했어요. 잠시 뒤 다시 시도해주세요.",
        });
      }
    } finally {
      setAdding(false);
    }
  }

  const savings = product.originalPrice - product.discountPrice;
  const hasDiscount = product.discountRate > 0;

  return (
    <div className="flex flex-1 flex-col">
      <div className="flex flex-1 flex-col gap-3 p-4 pb-28">
        <div role="img" aria-label={product.name} className="h-40 w-full rounded-md bg-border" />

        <h1 className="font-heading">{product.name}</h1>
        <p className="font-body text-text-weak">{product.description}</p>
        <p className="font-caption text-text-weak">{product.saleUnit}</p>

        {hasDiscount ? (
          <p className="font-caption text-text-weak line-through">
            {formatWon(product.originalPrice)}
          </p>
        ) : null}
        <p className="font-price text-discount">{formatWon(product.discountPrice)}</p>
        {hasDiscount ? (
          <div className="flex items-center gap-2">
            <Badge tone="discount">{`${product.discountRate}% 할인`}</Badge>
            <span className="font-caption text-text-weak">{`${formatWon(savings)} 아낌`}</span>
          </div>
        ) : null}

        <p className="font-caption text-text-weak">
          {product.soldOut
            ? "품절됐어요"
            : `남은 수량 ${product.availableQuantity}개${
                product.lowStock ? " · 소진 임박" : ""
              }`}
        </p>
        <p className="font-caption text-text-weak">{`오늘 ${formatKstTime(product.closingAt)} 마감`}</p>
        <p className="font-caption text-text-weak">{`1회 최대 ${product.maxOrderQuantity}개`}</p>
      </div>

      <div className="sticky bottom-0 flex flex-col gap-2 border-t border-border bg-surface p-4">
        {!canOperate ? (
          <p className="font-caption text-text-weak">지금은 담을 수 없어요</p>
        ) : atMax ? (
          <p className="font-caption text-text-weak">{`1회 최대 ${product.maxOrderQuantity}개까지 담을 수 있어요`}</p>
        ) : null}

        <div className="flex items-center gap-3">
          <QuantityStepper
            value={quantity}
            max={product.maxOrderQuantity}
            onChange={setQuantity}
            disabled={!canOperate}
          />
          <Button
            onClick={() => void handleAddToCart()}
            disabled={!canOperate || adding}
            className="flex-1"
          >
            {adding ? "담는 중이에요" : "장바구니 담기"}
          </Button>
        </div>
      </div>

      <BottomSheet open={result !== null} onClose={() => setResult(null)}>
        {result?.kind === "success" ? (
          <div className="flex flex-col gap-3">
            <p className="font-body text-text">장바구니에 담았어요</p>
            <Link
              href="/cart"
              className="font-body flex h-[52px] items-center justify-center rounded-md bg-brand px-5 font-medium text-on-brand"
            >
              장바구니 보기
            </Link>
          </div>
        ) : result?.kind === "limitExceeded" ? (
          <div className="flex flex-col gap-3">
            <p className="font-body text-text">{result.message}</p>
            <Link
              href="/cart"
              className="font-body flex h-[52px] items-center justify-center rounded-md bg-brand px-5 font-medium text-on-brand"
            >
              장바구니로 이동
            </Link>
          </div>
        ) : result?.kind === "error" ? (
          <div className="flex flex-col gap-3">
            <p className="font-body text-text">{result.message}</p>
            <Button variant="secondary" onClick={() => setResult(null)}>
              닫기
            </Button>
          </div>
        ) : null}
      </BottomSheet>
    </div>
  );
}
