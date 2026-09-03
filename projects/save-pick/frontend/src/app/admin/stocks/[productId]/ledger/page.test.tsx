import { describe, expect, it } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AdminAuthProvider } from "@/lib/auth/admin-auth";
import AdminStockLedgerPage from "./page";

const BASE = "http://test.local";

async function renderPage() {
  const page = await AdminStockLedgerPage({
    params: Promise.resolve({ productId: "12" }),
    searchParams: Promise.resolve({}),
  });
  return render(<AdminAuthProvider>{page}</AdminAuthProvider>);
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

function mockProductDetail() {
  server.use(
    http.get(`${BASE}/api/admin/products/12`, () =>
      HttpResponse.json({
        productId: 12,
        name: "삼겹살 500g",
        description: "국내산 삼겹살",
        saleUnit: "500g",
        originalPrice: 15000,
        closingAt: "2026-08-31T21:00:00+09:00",
        maxOrderQuantity: 5,
        status: "ON_SALE",
        currentDiscountRate: 30,
        currentPrice: 10500,
        nextDiscountRate: 50,
        nextDiscountAt: "2026-08-31T15:00:00+09:00",
        stock: {
          totalQuantity: 10,
          availableQuantity: 2,
          heldQuantity: 2,
          confirmedQuantity: 6,
          discardedQuantity: 0,
        },
      })
    )
  );
}

function mockLedger(items: unknown[]) {
  server.use(
    http.get(`${BASE}/api/admin/stocks/12/ledger`, () =>
      HttpResponse.json({
        items,
        page: { number: 0, size: 50, totalElements: items.length },
      })
    )
  );
}

const ALL_REASON_ITEMS = [
  {
    ledgerId: 1,
    reason: "ADMIN_ADJUST",
    orderNo: null,
    deltaTotal: 2,
    afterTotal: 10,
    actorType: "ADMIN",
    occurredAt: "2026-08-28T16:40:00+09:00",
  },
  {
    ledgerId: 2,
    reason: "HOLD",
    orderNo: "ORD-20260828-000117",
    deltaHeld: 2,
    afterAvailable: 2,
    actorType: "CUSTOMER",
    occurredAt: "2026-08-28T18:22:00+09:00",
  },
  {
    ledgerId: 3,
    reason: "CONFIRM",
    orderNo: "ORD-20260828-000117",
    deltaHeld: -2,
    deltaConfirmed: 2,
    afterAvailable: 2,
    actorType: "CUSTOMER",
    occurredAt: "2026-08-28T18:24:00+09:00",
  },
  {
    ledgerId: 4,
    reason: "HOLD_RELEASE",
    orderNo: "ORD-20260828-000102",
    deltaHeld: -1,
    afterAvailable: 5,
    actorType: "SYSTEM",
    note: "결제 3회 실패로 자동 해제",
    occurredAt: "2026-08-28T19:40:00+09:00",
  },
  {
    ledgerId: 5,
    reason: "HOLD_EXPIRE",
    orderNo: "ORD-20260828-000110",
    deltaHeld: -1,
    afterAvailable: 6,
    actorType: "SYSTEM",
    occurredAt: "2026-08-28T18:05:00+09:00",
  },
  {
    ledgerId: 6,
    reason: "CANCEL_RESTORE",
    orderNo: "ORD-20260828-000098",
    deltaConfirmed: -2,
    afterAvailable: 8,
    actorType: "CUSTOMER",
    occurredAt: "2026-08-28T17:31:00+09:00",
  },
  {
    ledgerId: 7,
    reason: "CANCEL_DISCARD",
    orderNo: "ORD-20260827-000131",
    deltaTotal: -1,
    deltaConfirmed: -1,
    afterTotal: 4,
    actorType: "CUSTOMER",
    note: "마감 시각이 지나 복구하지 않음",
    occurredAt: "2026-08-27T21:05:00+09:00",
  },
];

describe("SC-106 재고 변경 이력", () => {
  // TC-084·TC-123(X7): SC-106이 보여주는 사유 집합이 stock_ledgers.reason 7종과 정확히
  // 일치해야 한다(재검토에서 "노쇼 미복구"를 제거하고 7종으로 재정의한 건의 회귀 확인).
  it("TC-084 기본 상태: 최신순 이력과 상품명, 7종 필터를 보여준다", async () => {
    mockAuthenticatedSession();
    mockProductDetail();
    mockLedger(ALL_REASON_ITEMS);
    await renderPage();

    expect(await screen.findByText("삼겹살 500g")).toBeInTheDocument();
    const list = screen.getByRole("list");
    expect(within(list).getByText("관리자 조정")).toBeInTheDocument();
    expect(within(list).getAllByText(/주문 번호 ORD-20260828-000117/).length).toBeGreaterThan(0);
    expect(within(list).getByText("총 재고 8 → 10")).toBeInTheDocument();

    for (const label of [
      "전체",
      "관리자 조정",
      "선점",
      "확정 차감",
      "선점 해제",
      "선점 만료",
      "취소 복구",
      "취소 폐기",
    ]) {
      expect(screen.getByRole("button", { name: label })).toBeInTheDocument();
    }
  });

  it("로딩 상태: 행 스켈레톤을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/products/12`, async () => {
        await new Promise((resolve) => setTimeout(resolve, 30));
        return HttpResponse.json({
          productId: 12,
          name: "삼겹살 500g",
          description: "국내산 삼겹살",
          saleUnit: "500g",
          originalPrice: 15000,
          closingAt: "2026-08-31T21:00:00+09:00",
          maxOrderQuantity: 5,
          status: "ON_SALE",
          currentDiscountRate: 30,
          currentPrice: 10500,
          nextDiscountRate: 50,
          nextDiscountAt: "2026-08-31T15:00:00+09:00",
          stock: {
            totalQuantity: 10,
            availableQuantity: 2,
            heldQuantity: 2,
            confirmedQuantity: 6,
            discardedQuantity: 0,
          },
        });
      })
    );
    mockLedger([]);
    await renderPage();

    expect(screen.getAllByRole("status", { name: "불러오는 중" }).length).toBeGreaterThan(0);
    await screen.findByText("변경 이력이 없어요");
  });

  it("빈 상태: 변경 이력이 없어요 문구를 보여준다", async () => {
    mockAuthenticatedSession();
    mockProductDetail();
    mockLedger([]);
    await renderPage();

    expect(await screen.findByText("변경 이력이 없어요")).toBeInTheDocument();
  });

  it("사유 필터: 선점을 고르면 선점 이력만 보여준다", async () => {
    mockAuthenticatedSession();
    mockProductDetail();
    mockLedger(ALL_REASON_ITEMS);
    await renderPage();

    await screen.findByRole("list");
    const list = screen.getByRole("list");
    expect(within(list).getByText("관리자 조정")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "선점" }));

    expect(within(list).queryByText("관리자 조정")).not.toBeInTheDocument();
    expect(within(list).queryByText("취소 폐기")).not.toBeInTheDocument();
    expect(within(list).getByText("판매 가능 4 → 2")).toBeInTheDocument();
  });

  it("오류(통신): 불러오지 못했어요 문구와 다시 시도 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    mockProductDetail();
    server.use(
      http.get(`${BASE}/api/admin/stocks/12/ledger`, () =>
        HttpResponse.json(
          { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T00:00:00+09:00" },
          { status: 500 }
        )
      )
    );
    await renderPage();

    expect(await screen.findByText("불러오지 못했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });
});
