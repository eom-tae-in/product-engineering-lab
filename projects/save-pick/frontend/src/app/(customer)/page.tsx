import Link from "next/link";
import { fetchProducts } from "@/features/product/api";
import { fetchStoreInfo } from "@/features/store/api";
import { ProductListControls } from "@/features/product/components/ProductListControls";
import { RestrictedBanner } from "@/features/product/components/RestrictedBanner";
import type { ProductSort } from "@/features/product/types";
import { ProductCard } from "@/components/ui/ProductCard";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { RefreshButton } from "@/components/ui/RefreshButton";

const SORT_VALUES: ProductSort[] = ["CLOSING_SOON", "DISCOUNT_DESC", "PRICE_ASC"];

function parseSort(value: string | string[] | undefined): ProductSort {
  const raw = Array.isArray(value) ? value[0] : value;
  return (SORT_VALUES as string[]).includes(raw ?? "") ? (raw as ProductSort) : "CLOSING_SOON";
}

function parseHideSoldOut(value: string | string[] | undefined): boolean {
  const raw = Array.isArray(value) ? value[0] : value;
  return raw === "true";
}

/**
 * SC-001 · 홈 · 상품 목록 (docs/06-screen-list.md §3).
 * 비로그인도 볼 수 있는 조회 화면이라 Server Component에서 `serverGet`으로 직접
 * fetch한다(ARCHITECTURE.md 데이터 페칭 규칙). 정렬·품절 숨기기는 쿼리스트링으로
 * 표현해 이 화면 자체를 다시 요청하게 한다(`ProductListControls`).
 *
 * 판단(범위 제외): "마감으로 목록에서 빠진 상품 1회 안내 줄"은 연속 조회 간 비교가
 * 필요한데 이 슬라이스에는 폴링·실시간 갱신 인프라가 없어 구현하지 않는다. 다음
 * 슬라이스에서 재조회 주기가 정해지면 함께 추가한다.
 */
export default async function HomePage({ searchParams }: PageProps<"/">) {
  const params = await searchParams;
  const sort = parseSort(params.sort);
  const hideSoldOut = parseHideSoldOut(params.hideSoldOut);

  let store;
  let items;
  try {
    const [storeInfo, productList] = await Promise.all([
      fetchStoreInfo(),
      fetchProducts({ sort, hideSoldOut }),
    ]);
    store = storeInfo;
    // 품절 카드는 정렬 결과와 무관하게 항상 목록 끝으로 밀린다(docs/06 SC-001 "재고 소진").
    items = [...productList.items].sort((a, b) => Number(a.soldOut) - Number(b.soldOut));
  } catch {
    return (
      <div className="p-4">
        <ErrorState message="상품을 불러오지 못했어요" action={<RefreshButton />} />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3 p-4">
      <div className="flex items-center justify-between">
        <p className="font-heading">{store.name}</p>
        <p className="font-caption text-text-weak">{`${store.openTime}~${store.closeTime}`}</p>
      </div>

      <RestrictedBanner />

      <Link
        href="/search"
        className="font-body flex h-12 items-center rounded-md border border-border bg-surface px-4 text-text-weak"
      >
        상품명으로 검색
      </Link>

      <ProductListControls sort={sort} hideSoldOut={hideSoldOut} />

      {items.length === 0 ? (
        <EmptyState
          message="지금 판매 중인 상품이 없어요"
          reason="매장 영업시간은 10:00~22:00입니다"
          action={<RefreshButton label="새로고침" />}
        />
      ) : (
        <div className="flex flex-col gap-3">
          {items.map((item) => (
            <ProductCard key={item.productId} product={item} />
          ))}
        </div>
      )}
    </div>
  );
}
