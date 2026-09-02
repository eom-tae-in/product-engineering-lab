import { describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AdminAuthProvider } from "@/lib/auth/admin-auth";
import { kstDateString, setServerTimeOffsetMs } from "@/lib/server-time";
import AdminNoShowListPage from "./page";

const BASE = "http://test.local";

function renderPage() {
  return render(
    <AdminAuthProvider>
      <AdminNoShowListPage />
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

function noShowItem(overrides: Record<string, unknown> = {}) {
  return {
    orderId: 2001,
    orderNo: "ORD-20260828-000063",
    pickupNumber: "011",
    customerName: "최유리",
    status: "NO_SHOW",
    pickupStartAt: "2026-08-28T17:00:00+09:00",
    pickupEndAt: "2026-08-28T17:30:00+09:00",
    noShowDueAt: "2026-08-28T18:00:00+09:00",
    totalAmount: 9800,
    itemCount: 1,
    ...overrides,
  };
}

function upcomingItem(overrides: Record<string, unknown> = {}) {
  return {
    orderId: 3001,
    orderNo: "ORD-20260828-000117",
    pickupNumber: "042",
    customerName: "김지현",
    status: "CONFIRMED",
    pickupStartAt: "2026-08-28T20:00:00+09:00",
    pickupEndAt: "2026-08-28T20:30:00+09:00",
    noShowDueAt: "2026-08-28T21:00:00+09:00",
    totalAmount: 18900,
    itemCount: 2,
    ...overrides,
  };
}

/** 상태별로 CONFIRMED·READY·NO_SHOW 목록을 각각 목킹한다(SC-112가 3번 조회한다). */
function mockOrdersByStatus({
  noShow = [],
  confirmed = [],
  ready = [],
}: {
  noShow?: unknown[];
  confirmed?: unknown[];
  ready?: unknown[];
}) {
  server.use(
    http.get(`${BASE}/api/admin/orders`, ({ request }) => {
      const status = new URL(request.url).searchParams.get("status");
      const items = status === "NO_SHOW" ? noShow : status === "READY" ? ready : confirmed;
      return HttpResponse.json({
        items,
        page: { number: 0, size: 20, totalElements: items.length },
      });
    })
  );
}

describe("SC-112 노쇼 목록", () => {
  it("기본 상태(노쇼 탭): 주문 번호·픽업 번호·픽업 시간대·노쇼 판정 시각·결제 금액을 보여준다", async () => {
    mockAuthenticatedSession();
    mockOrdersByStatus({ noShow: [noShowItem()] });
    renderPage();

    const card = (await screen.findByText("011")).closest("a") as HTMLElement;
    expect(card).toHaveTextContent("최유리");
    expect(card).toHaveTextContent("9,800원");
    expect(card).toHaveTextContent("픽업 17:00~17:30 · 노쇼 판정 18:00");
    expect(card).toHaveTextContent("ORD-20260828-000063");
  });

  it("로딩 상태: 행 스켈레톤을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/orders`, async () => {
        await new Promise((resolve) => setTimeout(resolve, 30));
        return HttpResponse.json({ items: [], page: { number: 0, size: 20, totalElements: 0 } });
      })
    );
    renderPage();

    expect(screen.getAllByRole("status", { name: "불러오는 중" }).length).toBeGreaterThan(0);
    await screen.findByText("오늘 노쇼로 처리된 주문이 없어요");
  });

  it("빈 상태(노쇼 탭): 안내 문구와 전환 예정 보기 버튼을 보여주고, 누르면 전환 예정 탭으로 바뀐다", async () => {
    mockAuthenticatedSession();
    mockOrdersByStatus({ noShow: [], confirmed: [upcomingItem()] });
    renderPage();

    expect(await screen.findByText("오늘 노쇼로 처리된 주문이 없어요")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "전환 예정 보기" }));

    expect(await screen.findByText("042")).toBeInTheDocument();
  });

  it("오류: 불러오지 못했어요 문구와 다시 시도 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/orders`, () =>
        HttpResponse.json(
          { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-28T18:00:00+09:00" },
          { status: 500 }
        )
      )
    );
    renderPage();

    expect(await screen.findByText("불러오지 못했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("전환 예정 탭: CONFIRMED·READY 주문과 전환 예정 시각을 보여준다", async () => {
    mockAuthenticatedSession();
    mockOrdersByStatus({
      noShow: [],
      confirmed: [upcomingItem()],
      ready: [
        upcomingItem({
          orderId: 3002,
          pickupNumber: "038",
          customerName: "박서준",
          status: "READY",
          pickupStartAt: "2026-08-28T19:00:00+09:00",
          pickupEndAt: "2026-08-28T19:30:00+09:00",
          noShowDueAt: "2026-08-28T20:00:00+09:00",
          totalAmount: 7500,
        }),
      ],
    });
    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "전환 예정" }));

    const card1 = (await screen.findByText("042")).closest("a") as HTMLElement;
    expect(card1).toHaveTextContent("픽업 20:00~20:30 · 전환 예정 21:00");
    const card2 = screen.getByText("038").closest("a") as HTMLElement;
    expect(card2).toHaveTextContent("픽업 19:00~19:30 · 전환 예정 20:00");
  });

  it("전환 예정 탭 빈 상태: 전환 예정인 주문이 없어요 문구를 보여준다", async () => {
    mockAuthenticatedSession();
    mockOrdersByStatus({ noShow: [noShowItem()], confirmed: [], ready: [] });
    renderPage();
    await screen.findByText("011");

    await userEvent.click(screen.getByRole("button", { name: "전환 예정" }));

    expect(await screen.findByText("전환 예정인 주문이 없어요")).toBeInTheDocument();
  });

  it("전환 예정 배지: 남은 유예가 15분 이하이면 유예 NN분 남음 배지를 추가로 보여준다", async () => {
    mockAuthenticatedSession();
    // 서버 시각을 노쇼 전환 예정(19:30) 12분 전(19:18)으로 고정한다.
    setServerTimeOffsetMs(new Date("2026-08-28T19:18:00+09:00").getTime() - Date.now());
    mockOrdersByStatus({
      noShow: [],
      confirmed: [],
      ready: [
        upcomingItem({
          orderId: 3003,
          pickupNumber: "044",
          customerName: "이하늘",
          status: "READY",
          pickupStartAt: "2026-08-28T18:30:00+09:00",
          pickupEndAt: "2026-08-28T19:00:00+09:00",
          noShowDueAt: "2026-08-28T19:30:00+09:00",
          totalAmount: 12600,
        }),
      ],
    });
    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "전환 예정" }));

    const card = (await screen.findByText("044")).closest("a") as HTMLElement;
    await waitFor(() => expect(card).toHaveTextContent("유예 12분 남음"));

    setServerTimeOffsetMs(0);
  });

  it('SC-108 상태 필터 "노쇼"로도 진입할 수 있도록 오늘 픽업 날짜(kstDateString)로 조회한다', async () => {
    mockAuthenticatedSession();
    let requestedDate: string | null = null;
    server.use(
      http.get(`${BASE}/api/admin/orders`, ({ request }) => {
        const url = new URL(request.url);
        if (url.searchParams.get("status") === "NO_SHOW") {
          requestedDate = url.searchParams.get("pickupDate");
        }
        return HttpResponse.json({ items: [], page: { number: 0, size: 20, totalElements: 0 } });
      })
    );
    renderPage();

    await screen.findByText("오늘 노쇼로 처리된 주문이 없어요");
    expect(requestedDate).toBe(kstDateString(0));
  });
});
