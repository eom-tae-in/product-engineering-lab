import Link from "next/link";
import { Badge } from "./Badge";
import { formatKstTime, formatWon } from "@/lib/format";

export interface ProductCardData {
  productId: number;
  name: string;
  originalPrice: number;
  discountRate: number;
  discountPrice: number;
  availableQuantity: number;
  lowStock: boolean;
  soldOut: boolean;
  closingAt: string;
}

export interface ProductCardProps {
  product: ProductCardData;
}

/**
 * docs/09-ui-design-brief.md §2.4 상품 카드 규격 (SC-001, SC-002 공용).
 * - 할인율 0%(마감 24시간 초과)면 정가 취소선·할인 배지를 감춘다
 * - 품절이면 카드 전체 불투명도를 낮추고 우상단에 `품절` 배지를 둔다(문구 사전 "품절됐어요")
 */
export function ProductCard({ product }: ProductCardProps) {
  const {
    productId,
    name,
    originalPrice,
    discountRate,
    discountPrice,
    availableQuantity,
    lowStock,
    soldOut,
    closingAt,
  } = product;

  const hasDiscount = discountRate > 0;
  const savings = originalPrice - discountPrice;

  return (
    <Link
      href={`/products/${productId}`}
      className={`flex gap-3 rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)] ${
        soldOut ? "opacity-[0.55]" : ""
      }`}
    >
      <div
        role="img"
        aria-label={name}
        className="h-24 w-24 flex-none rounded-md bg-border"
      />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex items-start justify-between gap-2">
          <p className="font-body flex-1 truncate">{name}</p>
          {soldOut ? <Badge tone="soldout">품절</Badge> : null}
        </div>

        {hasDiscount ? (
          <p className="font-caption text-text-weak line-through">{formatWon(originalPrice)}</p>
        ) : null}
        <p className="font-price text-discount">{formatWon(discountPrice)}</p>

        {hasDiscount ? (
          <div className="flex items-center gap-2">
            <Badge tone="discount">{`${discountRate}% 할인`}</Badge>
            <span className="font-caption text-text-weak">{`${formatWon(savings)} 아낌`}</span>
          </div>
        ) : null}

        <p className="font-caption text-text-weak">
          {soldOut ? "품절됐어요" : `남은 수량 ${availableQuantity}개${lowStock ? " · 소진 임박" : ""}`}
        </p>
        <p className="font-caption text-text-weak">{`오늘 ${formatKstTime(closingAt)} 마감`}</p>
      </div>
    </Link>
  );
}
