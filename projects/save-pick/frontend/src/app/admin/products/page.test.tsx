import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AdminAuthProvider } from "@/lib/auth/admin-auth";
import AdminProductListPage from "./page";

const BASE = "http://test.local";

function renderPage() {
  return render(
    <AdminAuthProvider>
      <AdminProductListPage />
    </AdminAuthProvider>
  );
}

function mockAuthenticatedSession() {
  server.use(
    http.post(`${BASE}/api/auth/token/refresh`, () =>
      HttpResponse.json({
        accessToken: "admin-access-token",
        accessTokenExpiresAt: "2026-08-31T01:00:00+09:00",
      })
    )
  );
}

function sampleItem(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    productId: 12,
    name: "국내산 삼겹살 300g",
    status: "ON_SALE",
    originalPrice: 12000,
    currentDiscountRate: 30,
    currentPrice: 8400,
    nextDiscountRate: 50,
    nextDiscountAt: "2026-08-31T15:00:00+09:00",
    closingAt: "2026-08-31T21:00:00+09:00",
    totalQuantity: 20,
    availableQuantity: 20,
    ...overrides,
  };
}

function mockList(items: unknown[]) {
  server.use(
    http.get(`${BASE}/api/admin/products`, () =>
      HttpResponse.json({
        serverTime: "2026-08-31T09:35:00+09:00",
        items,
        page: { number: 0, size: 20, totalElements: items.length },
      })
    )
  );
}

describe("SC-103 상품 관리 목록", () => {
  it("기본 상태: 상품 행 목록과 상태 필터를 보여준다", async () => {
    mockAuthenticatedSession();
    mockList([sampleItem()]);
    renderPage();

    expect(await screen.findByText("국내산 삼겹살 300g")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "전체" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "상품 등록" })).toHaveAttribute(
      "href",
      "/admin/products/new"
    );
  });

  it("로딩 상태: 행 스켈레톤을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/products`, async () => {
        await new Promise((resolve) => setTimeout(resolve, 30));
        return HttpResponse.json({
          serverTime: "2026-08-31T09:35:00+09:00",
          items: [],
          page: { number: 0, size: 20, totalElements: 0 },
        });
      })
    );
    renderPage();

    expect(screen.getAllByRole("status", { name: "불러오는 중" }).length).toBeGreaterThan(0);
    await screen.findByText("등록된 상품이 없어요");
  });

  it("빈 상태: 등록된 상품이 없어요 문구와 상품 등록하기 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    mockList([]);
    renderPage();

    expect(await screen.findByText("등록된 상품이 없어요")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "상품 등록하기" })).toHaveAttribute(
      "href",
      "/admin/products/new"
    );
  });

  it("재고 소진: 판매 가능 0 배지를 보여준다", async () => {
    mockAuthenticatedSession();
    mockList([sampleItem({ availableQuantity: 0 })]);
    renderPage();

    expect(await screen.findByText("판매 가능 0")).toBeInTheDocument();
  });

  it("오류(통신): 불러오지 못했어요 문구와 다시 시도 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/products`, () =>
        HttpResponse.json(
          { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T00:00:00+09:00" },
          { status: 500 }
        )
      )
    );
    renderPage();

    expect(await screen.findByText("불러오지 못했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("오류(ON_SALE 불가): 재고 미등록 상품을 판매 시작하려 하면 안내 시트를 보여준다", async () => {
    mockAuthenticatedSession();
    mockList([sampleItem({ productId: 20, status: "DRAFT", name: "재고 없는 상품" })]);
    server.use(
      http.patch(`${BASE}/api/admin/products/20/status`, () =>
        HttpResponse.json(
          {
            code: "PRODUCT_STATUS_TRANSITION_DENIED",
            message: "전환할 수 없어요",
            serverTime: "2026-08-31T00:00:00+09:00",
          },
          { status: 409 }
        )
      )
    );
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "판매 시작" }));

    expect(
      await screen.findByText("재고를 먼저 등록해야 판매할 수 있어요")
    ).toBeInTheDocument();
  });

  it("오류(CLOSED 되돌리기): 마감된 상품을 되돌리려 하면 안내 시트를 보여준다", async () => {
    mockAuthenticatedSession();
    mockList([sampleItem({ productId: 30, status: "CLOSED", name: "마감된 상품" })]);
    server.use(
      http.patch(`${BASE}/api/admin/products/30/status`, () =>
        HttpResponse.json(
          {
            code: "PRODUCT_STATUS_TRANSITION_DENIED",
            message: "전환할 수 없어요",
            serverTime: "2026-08-31T00:00:00+09:00",
          },
          { status: 409 }
        )
      )
    );
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "판매 재개" }));

    expect(
      await screen.findByText("마감된 상품은 다시 판매할 수 없어요. 새 상품으로 등록해주세요")
    ).toBeInTheDocument();
  });
});
