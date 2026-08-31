import Link from "next/link";
import { fetchProducts } from "@/features/product/api";
import { SearchBox } from "@/features/product/components/SearchBox";
import { ProductCard } from "@/components/ui/ProductCard";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { RefreshButton } from "@/components/ui/RefreshButton";

function firstValue(value: string | string[] | undefined): string {
  return (Array.isArray(value) ? value[0] : value) ?? "";
}

/**
 * SC-002 · 상품 검색 (docs/06-screen-list.md §3).
 * 비로그인도 볼 수 있는 조회 화면이라 Server Component에서 직접 fetch한다
 * (ARCHITECTURE.md 데이터 페칭 규칙). 검색어는 쿼리스트링(`keyword`)으로 표현한다.
 *
 * 판단: "검색어가 비면 전체 목록과 같은 결과를 보여준다"(FR-011)는 API-010의 동작
 * 특성이다. 이 화면의 "빈 상태(검색 전)"는 06번이 별도로 `상품명으로 찾아보세요`
 * 안내만 표시하도록 못박고 있어, 검색어가 없을 때는 API를 호출하지 않고 안내만
 * 보여준다(불필요한 전체 목록 조회를 피한다).
 */
export default async function SearchPage({ searchParams }: PageProps<"/search">) {
  const params = await searchParams;
  const keyword = firstValue(params.keyword).trim();

  if (!keyword) {
    return (
      <div className="flex flex-col gap-3 p-4">
        <SearchBox initialKeyword="" />
        <EmptyState message="상품명으로 찾아보세요" />
      </div>
    );
  }

  let items;
  let totalElements = 0;
  try {
    const productList = await fetchProducts({ keyword });
    items = [...productList.items].sort((a, b) => Number(a.soldOut) - Number(b.soldOut));
    totalElements = productList.page.totalElements;
  } catch {
    return (
      <div className="flex flex-col gap-3 p-4">
        <SearchBox initialKeyword={keyword} />
        <ErrorState message="검색에 실패했어요" action={<RefreshButton />} />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3 p-4">
      <SearchBox initialKeyword={keyword} />

      {items.length === 0 ? (
        <EmptyState
          message={`"${keyword}"와 일치하는 판매 중 상품이 없어요`}
          action={
            <Link
              href="/"
              className="font-body flex h-11 w-auto items-center justify-center px-6 text-brand"
            >
              전체 상품 보기
            </Link>
          }
        />
      ) : (
        <>
          <p className="font-caption text-text-weak">{`"${keyword}" 검색 결과 ${totalElements}건`}</p>
          <div className="flex flex-col gap-3">
            {items.map((item) => (
              <ProductCard key={item.productId} product={item} />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
