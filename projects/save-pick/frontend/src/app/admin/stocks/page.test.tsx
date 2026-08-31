import { describe, expect, it } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AdminAuthProvider } from "@/lib/auth/admin-auth";
import AdminStockListPage from "./page";

const BASE = "http://test.local";

function renderPage() {
  return render(
    <AdminAuthProvider>
      <AdminStockListPage />
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
    name: "삼겹살 500g",
    status: "ON_SALE",
    totalQuantity: 10,
    availableQuantity: 2,
    heldQuantity: 2,
    confirmedQuantity: 6,
    discardedQuantity: 0,
    consistent: true,
    ...overrides,
  };
}

function mockList(items: unknown[]) {
  server.use(
    http.get(`${BASE}/api/admin/stocks`, () =>
      HttpResponse.json({
        serverTime: "2026-08-31T09:35:00+09:00",
        items,
        page: { number: 0, size: 20, totalElements: items.length },
      })
    )
  );
}

describe("SC-105 재고 현황·조정", () => {
  it("기본 상태: 상품별 4개 값 표와 합계 줄을 보여준다", async () => {
    mockAuthenticatedSession();
    mockList([sampleItem()]);
    renderPage();

    expect(await screen.findByText("삼겹살 500g")).toBeInTheDocument();
    expect(
      screen.getByText("총 재고 10 = 판매 가능 2 + 선점 중 2 + 확정 판매 6")
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "이력 보기" })).toHaveAttribute(
      "href",
      "/admin/stocks/12/ledger"
    );
    expect(screen.getByRole("button", { name: "전체" })).toBeInTheDocument();
  });

  it("로딩 상태: 표 스켈레톤을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/stocks`, async () => {
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
    await screen.findByText("재고를 등록한 상품이 없어요");
  });

  it("빈 상태: 재고를 등록한 상품이 없어요 문구를 보여준다", async () => {
    mockAuthenticatedSession();
    mockList([]);
    renderPage();

    expect(await screen.findByText("재고를 등록한 상품이 없어요")).toBeInTheDocument();
  });

  it("재고 소진: 판매 가능 0 행을 경고 캡션으로 구분한다", async () => {
    mockAuthenticatedSession();
    mockList([
      sampleItem({
        productId: 20,
        name: "훈제 오리 400g",
        totalQuantity: 8,
        availableQuantity: 0,
        heldQuantity: 1,
        confirmedQuantity: 7,
      }),
    ]);
    renderPage();

    const list = await screen.findByRole("list");
    expect(within(list).getByText("판매 가능 0")).toBeInTheDocument();
    expect(
      within(list).getByText("총 재고 8 = 판매 가능 0 + 선점 중 1 + 확정 판매 7")
    ).toBeInTheDocument();
  });

  it("판매 가능 0 필터를 누르면 onlyUnavailable 쿼리로 다시 조회한다", async () => {
    mockAuthenticatedSession();
    mockList([sampleItem()]);
    let lastUrl: string | null = null;
    server.use(
      http.get(`${BASE}/api/admin/stocks`, ({ request }) => {
        lastUrl = request.url;
        return HttpResponse.json({
          serverTime: "2026-08-31T09:35:00+09:00",
          items: [],
          page: { number: 0, size: 20, totalElements: 0 },
        });
      })
    );
    renderPage();

    await screen.findByText("재고를 등록한 상품이 없어요");
    await userEvent.click(screen.getByRole("button", { name: "판매 가능 0" }));

    expect(await screen.findByText("재고를 등록한 상품이 없어요")).toBeInTheDocument();
    expect(lastUrl).toContain("onlyUnavailable=true");
  });

  it("오류(통신): 불러오지 못했어요 문구와 다시 시도 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/stocks`, () =>
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

  it("조정 시트: 현재 값과 설정 가능한 최소값을 보여주고 저장하면 목록을 새로 불러온다", async () => {
    mockAuthenticatedSession();
    mockList([sampleItem()]);
    let requestedBody: unknown = null;
    server.use(
      http.put(`${BASE}/api/admin/products/12/stock`, async ({ request }) => {
        requestedBody = await request.json();
        return HttpResponse.json({
          productId: 12,
          before: { totalQuantity: 10, availableQuantity: 2, heldQuantity: 2, confirmedQuantity: 6 },
          after: { totalQuantity: 12, availableQuantity: 4, heldQuantity: 2, confirmedQuantity: 6 },
          minimumSettableQuantity: 8,
          changedAt: "2026-08-31T09:40:00+09:00",
        });
      })
    );
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "조정" }));

    expect(screen.getByText("삼겹살 500g 재고 조정")).toBeInTheDocument();
    expect(
      screen.getByText("현재 총 재고 10개 · 선점 중 2개 · 확정 판매 6개")
    ).toBeInTheDocument();
    expect(screen.getByText("설정 가능한 최소값 8개")).toBeInTheDocument();

    const input = screen.getByLabelText("새 총 재고");
    await userEvent.clear(input);
    await userEvent.type(input, "12");

    mockList([sampleItem({ totalQuantity: 12, availableQuantity: 4 })]);
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(requestedBody).toEqual({ totalQuantity: 12 });
    expect(
      await screen.findByText("총 재고 12 = 판매 가능 4 + 선점 중 2 + 확정 판매 6")
    ).toBeInTheDocument();
  });

  it("오류(축소 거부): 확정·선점 수량 안내와 함께 저장을 막는다", async () => {
    mockAuthenticatedSession();
    mockList([sampleItem()]);
    server.use(
      http.put(`${BASE}/api/admin/products/12/stock`, () =>
        HttpResponse.json(
          {
            code: "STOCK_BELOW_COMMITTED",
            message: "축소할 수 없어요",
            serverTime: "2026-08-31T00:00:00+09:00",
            details: { minimumSettableQuantity: 8 },
          },
          { status: 409 }
        )
      )
    );
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "조정" }));
    const input = screen.getByLabelText("새 총 재고");
    await userEvent.clear(input);
    await userEvent.type(input, "5");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(
      await screen.findByText("확정 6개와 선점 2개가 있어 8개 미만으로 줄일 수 없어요")
    ).toBeInTheDocument();
  });

  it("오류(값 형식): 음수를 입력하면 형식 오류를 보여주고 요청을 보내지 않는다", async () => {
    mockAuthenticatedSession();
    mockList([sampleItem()]);
    let putCalled = false;
    server.use(
      http.put(`${BASE}/api/admin/products/12/stock`, () => {
        putCalled = true;
        return HttpResponse.json({});
      })
    );
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "조정" }));
    const input = screen.getByLabelText("새 총 재고");
    await userEvent.clear(input);
    await userEvent.type(input, "-3");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(await screen.findByText("0 이상 정수만 입력할 수 있어요")).toBeInTheDocument();
    expect(putCalled).toBe(false);
  });
});
