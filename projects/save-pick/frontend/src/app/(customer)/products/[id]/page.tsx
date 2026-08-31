import Link from "next/link";
import { fetchProductDetail } from "@/features/product/api";
import { ProductDetailView } from "@/features/product/components/ProductDetailView";
import type { ProductDetailResponse } from "@/features/product/types";
import { ApiError } from "@/lib/api-client";
import { ErrorState } from "@/components/ui/ErrorState";
import { RefreshButton } from "@/components/ui/RefreshButton";
import { formatKstTime } from "@/lib/format";

interface ClosedInfo {
  name: string;
  closingAt: string | null;
}

type LoadResult =
  | { kind: "ok"; product: ProductDetailResponse }
  | { kind: "notFound" }
  | { kind: "closed"; info: ClosedInfo }
  | { kind: "error" };

async function loadProduct(id: string): Promise<LoadResult> {
  try {
    const product = await fetchProductDetail(id);
    return { kind: "ok", product };
  } catch (error) {
    if (error instanceof ApiError && error.code === "NOT_FOUND") {
      return { kind: "notFound" };
    }
    if (error instanceof ApiError && error.code === "PRODUCT_CLOSED") {
      return {
        kind: "closed",
        info: {
          name: typeof error.details?.name === "string" ? error.details.name : "상품",
          closingAt:
            typeof error.details?.closingAt === "string" ? error.details.closingAt : null,
        },
      };
    }
    return { kind: "error" };
  }
}

/**
 * SC-003 · 상품 상세 (docs/06-screen-list.md §3).
 * 비로그인도 볼 수 있는 조회 화면이라 Server Component에서 직접 fetch한다
 * (ARCHITECTURE.md 데이터 페칭 규칙). 수량 조절·담기 같은 상호작용은
 * `ProductDetailView`(클라이언트 컴포넌트)로 넘긴다.
 *
 * fetch 결과 분류(`loadProduct`)와 JSX 구성을 분리했다 — try/catch 안에서 바로 JSX를
 * 만들면 컴포넌트 렌더링 중 오류가 catch에 잡히지 않는데도 잡히는 것처럼 보인다
 * (react-hooks/error-boundaries 린트 규칙).
 *
 * 판단: API-011 오류 표에 있는 `PRODUCT_NOT_ON_SALE`(HIDDEN 상품)은 06번 SC-003
 * 상태표에 별도 정의가 없다. HIDDEN 상품은 정상 진입 경로(SC-001·SC-002 카드 탭)로는
 * 도달할 수 없어 발생 빈도가 낮다고 보고, 06이 정의한 "오류(통신)" 상태(정보를
 * 불러오지 못했어요 + 다시 시도)로 처리한다.
 */
export default async function ProductDetailPage({ params }: PageProps<"/products/[id]">) {
  const { id } = await params;
  const result = await loadProduct(id);

  if (result.kind === "notFound") {
    return (
      <div className="flex flex-col items-center gap-4 p-4 py-16 text-center">
        <p className="font-body text-text">상품을 찾을 수 없어요</p>
        <Link
          href="/"
          className="font-body flex h-[52px] w-auto items-center justify-center rounded-md bg-brand px-6 text-on-brand"
        >
          상품 목록으로
        </Link>
      </div>
    );
  }

  if (result.kind === "closed") {
    const { name, closingAt } = result.info;
    return (
      <div className="flex flex-col gap-4 p-4">
        <div className="rounded-md bg-border px-4 py-3">
          <p className="font-body text-text-weak">
            {closingAt
              ? `판매가 종료된 상품이에요 (${formatKstTime(closingAt)} 마감)`
              : "판매가 종료된 상품이에요"}
          </p>
        </div>
        <h1 className="font-heading">{name}</h1>
      </div>
    );
  }

  if (result.kind === "error") {
    return (
      <div className="p-4">
        <ErrorState message="정보를 불러오지 못했어요" action={<RefreshButton />} />
      </div>
    );
  }

  return <ProductDetailView product={result.product} />;
}
