"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { fetchAdminProducts, fetchDiscountPolicy } from "@/features/admin-product/api";
import type { AdminProductListItem, DiscountTier } from "@/features/admin-product/types";
import { formatKstTime, formatWon } from "@/lib/format";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { Skeleton } from "@/components/ui/Skeleton";

/**
 * SC-107 · 할인 구간 정책 (docs/06-screen-list.md §4).
 * 관리자 전용 화면이라 `app/admin/layout.tsx`의 AdminGate가 인증을 이미 보장한다.
 * 구간표는 API-108, 상품별 현재 구간은 API-102(status=ON_SALE)에서 가져온다.
 */
export default function AdminDiscountPolicyPage() {
  const [tiers, setTiers] = useState<DiscountTier[]>([]);
  const [products, setProducts] = useState<AdminProductListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // effect 안에서 호출해도 setState가 동기 실행되지 않도록 async/await 대신
  // .then()/.catch() 콜백 안에서만 setState한다(react-hooks/set-state-in-effect,
  // lib/auth/customer-auth.tsx의 refresh().then(...) 패턴과 동일).
  const runLoad = useCallback(() => {
    return Promise.all([fetchDiscountPolicy(), fetchAdminProducts({ status: "ON_SALE" })])
      .then(([policy, productList]) => {
        setTiers(policy.tiers);
        setProducts(productList.items);
        setLoadError(null);
      })
      .catch(() => {
        setLoadError("불러오지 못했어요");
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  // 재시도(버튼 클릭)에서만 쓴다. 로딩 화면으로 되돌린 뒤 다시 불러온다.
  const retryLoad = useCallback(() => {
    setLoading(true);
    setLoadError(null);
    runLoad();
  }, [runLoad]);

  useEffect(() => {
    runLoad();
  }, [runLoad]);

  return (
    <div className="flex flex-col gap-3 p-4">
      <h1 className="font-heading">할인 정책</h1>

      {loading ? (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-40 w-full" />
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-20 w-full" />
        </div>
      ) : loadError ? (
        <ErrorState message={loadError} onRetry={retryLoad} />
      ) : (
        <>
          <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
            <table className="w-full">
              <thead>
                <tr className="font-caption text-left text-text-weak">
                  <th scope="col" className="pb-2">
                    구간
                  </th>
                  <th scope="col" className="pb-2">
                    마감까지 남은 시간
                  </th>
                  <th scope="col" className="pb-2 text-right">
                    할인율
                  </th>
                </tr>
              </thead>
              <tbody>
                {tiers.map((tier) => (
                  <tr key={tier.code} className="font-body border-t border-border">
                    <td className="py-2">{tier.code}</td>
                    <td className="py-2">{tier.condition}</td>
                    <td className="py-2 text-right tabular-nums">{`${tier.discountRate}%`}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="font-caption mt-3 text-text-weak">
              할인율은 마감 시각으로 자동 계산되며 개별 주문의 할인율을 바꿀 수 없어요
            </p>
          </div>

          <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
            <h2 className="font-heading mb-3">판매 중 상품</h2>
            {products.length === 0 ? (
              <EmptyState message="현재 판매 중인 상품이 없어요" />
            ) : (
              <ul className="flex flex-col gap-2">
                {products.map((product) => (
                  <li key={product.productId} className="border-b border-border pb-2">
                    <Link href={`/admin/products/${product.productId}`}>
                      <p className="font-body truncate">{product.name}</p>
                      <p className="font-caption text-text-weak">
                        {`현재 적용 할인율 ${product.currentDiscountRate}% · ${formatWon(product.currentPrice)}`}
                      </p>
                      <p className="font-caption text-text-weak">
                        {product.nextDiscountAt
                          ? `다음 구간(${product.nextDiscountRate}%) 진입 ${formatKstTime(
                              product.nextDiscountAt
                            )}`
                          : "다음 구간 없음"}
                      </p>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      )}
    </div>
  );
}
