import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AuthProvider } from "@/lib/auth/customer-auth";
import StorePage from "./page";
import StoreLoading from "./loading";

const BASE = "http://test.local";

const refreshMock = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: refreshMock, replace: vi.fn(), push: vi.fn() }),
}));

function mockStoreInfo(overrides: Partial<Record<string, unknown>> = {}) {
  server.use(
    http.get(`${BASE}/api/store`, () =>
      HttpResponse.json({
        name: "savePick 신선마켓",
        address: "서울특별시 ○○구 ○○로 12",
        phone: "0212345678",
        openTime: "10:00",
        closeTime: "22:00",
        slotUnitMinutes: 30,
        ...overrides,
      })
    )
  );
}

function mockGuestSession() {
  server.use(
    http.post(`${BASE}/api/auth/token/refresh`, () =>
      HttpResponse.json(
        {
          code: "UNAUTHENTICATED",
          message: "로그인이 필요해요.",
          serverTime: "2026-08-31T00:00:00+09:00",
        },
        { status: 401 }
      )
    )
  );
}

function mockAuthenticatedSession() {
  server.use(
    http.post(`${BASE}/api/auth/token/refresh`, () =>
      HttpResponse.json({
        accessToken: "access-token",
        accessTokenExpiresAt: "2026-08-31T01:00:00+09:00",
      })
    )
  );
}

function listItem(overrides: Record<string, unknown> = {}) {
  return {
    orderId: 1001,
    orderNo: "ORD-20260831-000001",
    orderedAt: "2026-08-31T10:00:00+09:00",
    status: "CONFIRMED",
    pickupStartAt: "2026-08-31T19:30:00+09:00",
    pickupEndAt: "2026-08-31T20:00:00+09:00",
    pickupNumber: "017",
    totalAmount: 14100,
    itemSummary: "국내산 삼겹살 300g 외 1건",
    ...overrides,
  };
}

function mockOrders(items: unknown[]) {
  server.use(
    http.get(`${BASE}/api/orders`, () =>
      HttpResponse.json({ items, page: { number: 0, size: 20, totalElements: items.length } })
    )
  );
}

function mockOrderDetail(orderId: number, overrides: Record<string, unknown> = {}) {
  server.use(
    http.get(`${BASE}/api/orders/${orderId}`, () =>
      HttpResponse.json({
        serverTime: "2026-08-31T18:00:00+09:00",
        orderId,
        orderNo: "ORD-20260831-000001",
        status: "CONFIRMED",
        orderedAt: "2026-08-31T10:00:00+09:00",
        items: [],
        totalAmount: 14100,
        pickupNumber: "017",
        pickupStartAt: "2026-08-31T19:30:00+09:00",
        pickupEndAt: "2026-08-31T20:00:00+09:00",
        noShowDueAt: "2026-08-31T20:30:00+09:00",
        cancelable: true,
        cancelableUntil: "2026-08-31T18:30:00+09:00",
        cancelUnavailableReason: null,
        canceledBy: null,
        cancelReason: null,
        store: { name: "savePick 신선마켓", address: "서울 ○○구", phone: "0212345678" },
        statusHistory: [],
        ...overrides,
      })
    )
  );
}

async function renderPage() {
  return render(<AuthProvider>{await StorePage()}</AuthProvider>);
}

describe("SC-015 매장·픽업 안내", () => {
  it("기본 상태: 매장 정보와 픽업 절차 3단계를 보여준다", async () => {
    mockGuestSession();
    mockStoreInfo();

    await renderPage();

    expect(screen.getByText("savePick 신선마켓")).toBeInTheDocument();
    expect(screen.getByText("서울특별시 ○○구 ○○로 12")).toBeInTheDocument();
    expect(screen.getByText("0212345678")).toBeInTheDocument();
    expect(screen.getByText("10:00~22:00")).toBeInTheDocument();
    expect(screen.getByText("시간대에 방문")).toBeInTheDocument();
    expect(screen.getByText("픽업 번호 말하기")).toBeInTheDocument();
    expect(screen.getByText("수령 확인")).toBeInTheDocument();
    expect(
      screen.getByText("지도와 실시간 위치 안내는 제공하지 않아요")
    ).toBeInTheDocument();
  });

  it("확정 주문이 있으면 픽업 시간대와 노쇼 전환 예정 시각을 보여준다", async () => {
    mockAuthenticatedSession();
    mockStoreInfo();
    mockOrders([listItem()]);
    mockOrderDetail(1001);

    await renderPage();

    expect(await screen.findByText("2026-08-31 19:30~20:00")).toBeInTheDocument();
    expect(screen.getByText("20:30")).toBeInTheDocument();
    expect(screen.getByText("017")).toBeInTheDocument();
  });

  it("진행 중 주문이 여럿이면 가장 이른 픽업 한 건만 보여준다", async () => {
    mockAuthenticatedSession();
    mockStoreInfo();
    mockOrders([
      listItem({ orderId: 1002, pickupStartAt: "2026-08-31T21:00:00+09:00" }),
      listItem({ orderId: 1001, pickupStartAt: "2026-08-31T19:30:00+09:00" }),
    ]);
    mockOrderDetail(1001);

    await renderPage();

    // 더 이른 1001의 상세만 조회한다 — 1002를 골랐다면 이 문구가 나오지 않는다.
    expect(await screen.findByText("2026-08-31 19:30~20:00")).toBeInTheDocument();
  });

  it("빈 상태(비로그인): 예정된 픽업이 없어요를 보여주고 로그인으로 보내지 않는다", async () => {
    mockGuestSession();
    mockStoreInfo();

    await renderPage();

    expect(await screen.findByText("예정된 픽업이 없어요")).toBeInTheDocument();
    expect(refreshMock).not.toHaveBeenCalled();
  });

  it("빈 상태(진행 중 주문 없음): 예정된 픽업이 없어요를 보여준다", async () => {
    mockAuthenticatedSession();
    mockStoreInfo();
    mockOrders([]);

    await renderPage();

    expect(await screen.findByText("예정된 픽업이 없어요")).toBeInTheDocument();
  });

  it("오류(예정된 픽업 조회 실패): 다시 시도하면 복구된다", async () => {
    mockAuthenticatedSession();
    mockStoreInfo();
    let attempt = 0;
    server.use(
      http.get(`${BASE}/api/orders`, () => {
        attempt += 1;
        if (attempt === 1) {
          return HttpResponse.json(
            { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T00:00:00+09:00" },
            { status: 500 }
          );
        }
        return HttpResponse.json({
          items: [listItem()],
          page: { number: 0, size: 20, totalElements: 1 },
        });
      })
    );
    mockOrderDetail(1001);

    await renderPage();

    expect(await screen.findByText("예정된 픽업을 불러오지 못했어요")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(await screen.findByText("2026-08-31 19:30~20:00")).toBeInTheDocument();
  });

  it("로딩 상태: 카드 스켈레톤을 보여준다", () => {
    render(<StoreLoading />);

    expect(screen.getAllByRole("status", { name: "불러오는 중" }).length).toBe(3);
  });

  it("오류: 매장 정보를 불러오지 못했어요 문구와 다시 시도 버튼을 보여준다", async () => {
    mockGuestSession();
    server.use(
      http.get(`${BASE}/api/store`, () =>
        HttpResponse.json(
          {
            code: "INTERNAL_ERROR",
            message: "서버 오류",
            serverTime: "2026-08-31T00:00:00+09:00",
          },
          { status: 500 }
        )
      )
    );

    await renderPage();

    expect(screen.getByText("매장 정보를 불러오지 못했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });
});
