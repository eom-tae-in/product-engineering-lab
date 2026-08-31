import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import SearchPage from "./page";
import SearchLoading from "./loading";

const BASE = "http://test.local";

const pushMock = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock, refresh: vi.fn() }),
  usePathname: () => "/search",
}));

function sampleProduct(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    productId: 12,
    name: "국내산 삼겹살 300g",
    saleUnit: "300g",
    originalPrice: 12000,
    discountRate: 30,
    discountPrice: 8400,
    availableQuantity: 8,
    lowStock: false,
    soldOut: false,
    closingAt: "2026-08-31T21:00:00+09:00",
    nextDiscountAt: null,
    ...overrides,
  };
}

function mockProducts(items: unknown[]) {
  server.use(
    http.get(`${BASE}/api/products`, () =>
      HttpResponse.json({
        serverTime: "2026-08-31T10:00:00+09:00",
        items,
        page: { number: 0, size: 20, totalElements: items.length },
      })
    )
  );
}

async function renderSearch(keyword?: string) {
  const page = await SearchPage({
    params: Promise.resolve({}),
    searchParams: Promise.resolve(keyword ? { keyword } : {}),
  });
  return render(page);
}

describe("SC-002 상품 검색", () => {
  it("빈 상태(검색 전): 상품명으로 찾아보세요 안내만 보여준다", async () => {
    await renderSearch();

    expect(screen.getByText("상품명으로 찾아보세요")).toBeInTheDocument();
  });

  it("기본 상태: 검색 결과 카드와 건수를 보여준다", async () => {
    mockProducts([sampleProduct()]);

    await renderSearch("삼겹");

    expect(await screen.findByText('"삼겹" 검색 결과 1건')).toBeInTheDocument();
    expect(screen.getByText("국내산 삼겹살 300g")).toBeInTheDocument();
  });

  it("빈 상태(결과 없음): 일치하는 상품이 없어요 문구와 전체 상품 보기 버튼을 보여준다", async () => {
    mockProducts([]);

    await renderSearch("없는상품");

    expect(
      await screen.findByText('"없는상품"와 일치하는 판매 중 상품이 없어요')
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "전체 상품 보기" })).toBeInTheDocument();
  });

  it("오류: 검색에 실패했어요 문구와 다시 시도 버튼을 보여준다", async () => {
    server.use(
      http.get(`${BASE}/api/products`, () =>
        HttpResponse.json(
          { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T00:00:00+09:00" },
          { status: 500 }
        )
      )
    );

    await renderSearch("삼겹");

    expect(await screen.findByText("검색에 실패했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("로딩 상태: 카드 스켈레톤을 보여준다", () => {
    render(<SearchLoading />);
    expect(screen.getAllByRole("status", { name: "불러오는 중" }).length).toBeGreaterThanOrEqual(3);
  });
});
