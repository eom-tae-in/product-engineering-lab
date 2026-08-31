import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AuthProvider } from "@/lib/auth/customer-auth";
import HomePage from "./page";
import HomeLoading from "./loading";

const BASE = "http://test.local";

const pushMock = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock, refresh: vi.fn() }),
  usePathname: () => "/",
}));

function mockGuestSession() {
  server.use(
    http.post(`${BASE}/api/auth/token/refresh`, () =>
      HttpResponse.json(
        {
          code: "UNAUTHENTICATED",
          message: "인증이 필요해요.",
          serverTime: "2026-08-31T00:00:00+09:00",
        },
        { status: 401 }
      )
    )
  );
}

function mockStore() {
  server.use(
    http.get(`${BASE}/api/store`, () =>
      HttpResponse.json({
        name: "savePick 신선마켓",
        address: "서울특별시 ○○구 ○○로 12",
        phone: "0212345678",
        openTime: "10:00",
        closeTime: "22:00",
        slotUnitMinutes: 30,
      })
    )
  );
}

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

async function renderHome(params: Record<string, string> = {}) {
  const page = await HomePage({
    params: Promise.resolve({}),
    searchParams: Promise.resolve(params),
  });
  return render(<AuthProvider>{page}</AuthProvider>);
}

describe("SC-001 홈 · 상품 목록", () => {
  it("기본 상태: 매장 정보와 상품 카드 목록을 보여준다", async () => {
    mockGuestSession();
    mockStore();
    mockProducts([sampleProduct()]);

    await renderHome();

    expect(await screen.findByText("savePick 신선마켓")).toBeInTheDocument();
    expect(screen.getByText("10:00~22:00")).toBeInTheDocument();
    expect(screen.getByText("국내산 삼겹살 300g")).toBeInTheDocument();
    expect(screen.getByText("남은 수량 8개")).toBeInTheDocument();
  });

  it("로딩 상태: 카드 스켈레톤을 보여준다", () => {
    render(<HomeLoading />);
    expect(screen.getAllByRole("status", { name: "불러오는 중" }).length).toBeGreaterThanOrEqual(4);
  });

  it("빈 상태: 지금 판매 중인 상품이 없어요 문구와 새로고침 버튼을 보여준다", async () => {
    mockGuestSession();
    mockStore();
    mockProducts([]);

    await renderHome();

    expect(await screen.findByText("지금 판매 중인 상품이 없어요")).toBeInTheDocument();
    expect(screen.getByText("매장 영업시간은 10:00~22:00입니다")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "새로고침" })).toBeInTheDocument();
  });

  it("오류: 상품을 불러오지 못했어요 문구와 다시 시도 버튼을 보여준다", async () => {
    mockGuestSession();
    server.use(
      http.get(`${BASE}/api/store`, () =>
        HttpResponse.json(
          { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T00:00:00+09:00" },
          { status: 500 }
        )
      )
    );
    mockProducts([]);

    await renderHome();

    expect(await screen.findByText("상품을 불러오지 못했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("재고 소진 카드는 정렬 결과와 무관하게 목록 끝으로 밀린다", async () => {
    mockGuestSession();
    mockStore();
    mockProducts([
      sampleProduct({ productId: 1, name: "품절 상품", soldOut: true, availableQuantity: 0 }),
      sampleProduct({ productId: 2, name: "판매중 상품" }),
    ]);

    const { container } = await renderHome();
    await screen.findByText("판매중 상품");

    const html = container.innerHTML;
    expect(html.indexOf("판매중 상품")).toBeLessThan(html.indexOf("품절 상품"));
  });
});
