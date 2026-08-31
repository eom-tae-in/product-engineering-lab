import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AdminAuthProvider } from "@/lib/auth/admin-auth";
import AdminDiscountPolicyPage from "./page";

const BASE = "http://test.local";

function renderPage() {
  return render(
    <AdminAuthProvider>
      <AdminDiscountPolicyPage />
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

function mockPolicy() {
  server.use(
    http.get(`${BASE}/api/admin/discount-policy`, () =>
      HttpResponse.json({
        tiers: [
          { code: "D0", condition: "24시간 초과", discountRate: 0 },
          { code: "D1", condition: "24시간 이하 ~ 6시간 초과", discountRate: 30 },
          { code: "D2", condition: "6시간 이하 ~ 2시간 초과", discountRate: 50 },
          { code: "D3", condition: "2시간 이하 ~ 마감 이전", discountRate: 70 },
        ],
        rounding: "10원 단위 내림",
        minimumPrice: 100,
        boundaryRule: "경계값은 더 큰 할인율 구간에 속한다",
        editable: false,
      })
    )
  );
}

function mockProducts(items: unknown[]) {
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

describe("SC-107 할인 구간 정책", () => {
  it("기본 상태: 구간표와 상품별 현재 구간을 보여준다", async () => {
    mockAuthenticatedSession();
    mockPolicy();
    mockProducts([sampleItem()]);
    renderPage();

    expect(await screen.findByText("24시간 초과")).toBeInTheDocument();
    expect(screen.getByText("24시간 이하 ~ 6시간 초과")).toBeInTheDocument();
    expect(
      screen.getByText("할인율은 마감 시각으로 자동 계산되며 개별 주문의 할인율을 바꿀 수 없어요")
    ).toBeInTheDocument();
    expect(screen.getByText("국내산 삼겹살 300g")).toBeInTheDocument();
    expect(screen.getByText("현재 적용 할인율 30% · 8,400원")).toBeInTheDocument();
    expect(screen.getByText("다음 구간(50%) 진입 15:00")).toBeInTheDocument();
  });

  it("로딩 상태: 목록 스켈레톤을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/discount-policy`, async () => {
        await new Promise((resolve) => setTimeout(resolve, 30));
        return HttpResponse.json({
          tiers: [],
          rounding: "10원 단위 내림",
          minimumPrice: 100,
          boundaryRule: "경계값은 더 큰 할인율 구간에 속한다",
          editable: false,
        });
      })
    );
    mockProducts([]);
    renderPage();

    expect(screen.getAllByRole("status", { name: "불러오는 중" }).length).toBeGreaterThan(0);
    await screen.findByText("현재 판매 중인 상품이 없어요");
  });

  it("빈 상태: 판매 중 상품이 없으면 안내 문구를 보여준다", async () => {
    mockAuthenticatedSession();
    mockPolicy();
    mockProducts([]);
    renderPage();

    expect(await screen.findByText("현재 판매 중인 상품이 없어요")).toBeInTheDocument();
  });

  it("오류(통신): 불러오지 못했어요 문구와 다시 시도 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/discount-policy`, () =>
        HttpResponse.json(
          { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T00:00:00+09:00" },
          { status: 500 }
        )
      )
    );
    mockProducts([]);
    renderPage();

    expect(await screen.findByText("불러오지 못했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });
});
