"use client";

import { useCallback, useEffect, useState } from "react";
import { fetchAdminProductDetail } from "../api";
import type { AdminProductDetailResponse } from "../types";
import { ProductForm } from "./ProductForm";
import { Skeleton } from "@/components/ui/Skeleton";
import { ErrorState } from "@/components/ui/ErrorState";

export interface AdminProductEditViewProps {
  productId: number;
}

/**
 * SC-104 · 상품 수정 (docs/06-screen-list.md §4 "기본(수정)").
 * 관리자 전용 화면이라 `app/admin/layout.tsx`의 AdminGate가 인증을 이미 보장한다.
 * Client Component에서 마운트 시 `authScope: "admin"`으로 직접 호출한다
 * (ARCHITECTURE.md 데이터 페칭 규칙).
 */
export function AdminProductEditView({ productId }: AdminProductEditViewProps) {
  const [product, setProduct] = useState<AdminProductDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // effect 안에서 호출해도 setState가 동기 실행되지 않도록 async/await 대신
  // .then()/.catch() 콜백 안에서만 setState한다(react-hooks/set-state-in-effect,
  // lib/auth/customer-auth.tsx의 refresh().then(...) 패턴과 동일).
  const runLoad = useCallback(() => {
    return fetchAdminProductDetail(productId)
      .then((data) => {
        setProduct(data);
        setLoadError(null);
      })
      .catch(() => {
        setLoadError("불러오지 못했어요");
      })
      .finally(() => {
        setLoading(false);
      });
  }, [productId]);

  // 재시도(버튼 클릭)에서만 쓴다. 로딩 화면으로 되돌린 뒤 다시 불러온다.
  const retryLoad = useCallback(() => {
    setLoading(true);
    setLoadError(null);
    runLoad();
  }, [runLoad]);

  useEffect(() => {
    runLoad();
  }, [runLoad]);

  if (loading) {
    return (
      <div className="flex flex-col gap-3 p-4">
        <Skeleton className="h-6 w-24" />
        <Skeleton className="h-[52px] w-full" />
        <Skeleton className="h-[52px] w-full" />
        <Skeleton className="h-[52px] w-full" />
      </div>
    );
  }

  if (loadError || !product) {
    return (
      <div className="p-4">
        <ErrorState message={loadError ?? "불러오지 못했어요"} onRetry={retryLoad} />
      </div>
    );
  }

  return <ProductForm mode="edit" productId={productId} initialProduct={product} />;
}
