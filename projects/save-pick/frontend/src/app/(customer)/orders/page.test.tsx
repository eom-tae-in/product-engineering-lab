import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AuthProvider } from "@/lib/auth/customer-auth";
import OrdersPage from "./page";

const BASE = "http://test.local";
const replaceMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: replaceMock }),
}));

function renderPage() {
  return render(
    <AuthProvider>
      <OrdersPage />
    </AuthProvider>
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

function mockGuestSession() {
  server.use(
    http.post(`${BASE}/api/auth/token/refresh`, () =>
      HttpResponse.json(
        { code: "UNAUTHENTICATED", message: "로그인이 필요해요.", serverTime: "2026-08-31T00:00:00+09:00" },
        { status: 401 }
      )
    )
  );
}

function sampleItem(overrides: Record<string, unknown> = {}) {
  return {
    orderId: 1001,
    orderNo: "ORD-20260828-000123",
    orderedAt: "2026-08-28T18:22:00+09:00",
    status: "CONFIRMED",
    pickupStartAt: "2026-08-28T20:00:00+09:00",
    pickupEndAt: "2026-08-28T20:30:00+09:00",
    pickupNumber: "042",
    totalAmount: 18900,
    itemSummary: "삼겹살 500g 외 1건",
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

describe("SC-009 주문 내역", () => {
  it("기본 상태: 최신순 주문 카드 목록을 보여준다", async () => {
    mockAuthenticatedSession();
    mockOrders([sampleItem()]);
    renderPage();

    expect(await screen.findByText("확정")).toBeInTheDocument();
    expect(screen.getByText("ORD-20260828-000123")).toBeInTheDocument();
    expect(screen.getByText("픽업 번호 042")).toBeInTheDocument();
    expect(screen.getByText("18,900원")).toBeInTheDocument();
  });

  it("로딩 상태: 카드 스켈레톤을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/orders`, async () => {
        await new Promise((resolve) => setTimeout(resolve, 30));
        return HttpResponse.json({ items: [], page: { number: 0, size: 20, totalElements: 0 } });
      })
    );
    renderPage();

    expect(screen.getAllByRole("status", { name: "불러오는 중" }).length).toBeGreaterThan(0);
    await screen.findByText("아직 주문이 없어요");
  });

  it("빈 상태(전체): 아직 주문이 없어요 문구와 이동 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    mockOrders([]);
    renderPage();

    expect(await screen.findByText("아직 주문이 없어요")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "마감 할인 상품 보러 가기" })).toHaveAttribute("href", "/");
  });

  it('빈 상태(필터): "취소"에 해당하는 주문이 없어요 문구와 전체 보기 버튼을 보여준다', async () => {
    mockAuthenticatedSession();
    mockOrders([sampleItem()]);
    renderPage();
    await screen.findByText("ORD-20260828-000123");

    mockOrders([]);
    await userEvent.click(screen.getByRole("button", { name: "취소" }));

    expect(await screen.findByText('"취소"에 해당하는 주문이 없어요')).toBeInTheDocument();

    mockOrders([sampleItem()]);
    await userEvent.click(screen.getByRole("button", { name: "전체 보기" }));
    expect(await screen.findByText("ORD-20260828-000123")).toBeInTheDocument();
  });

  it("오류: 주문 내역을 불러오지 못했어요 문구와 다시 시도 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/orders`, () =>
        HttpResponse.json(
          { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T00:00:00+09:00" },
          { status: 500 }
        )
      )
    );
    renderPage();

    expect(await screen.findByText("주문 내역을 불러오지 못했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("상태 필터를 고르면 해당 status로 API를 다시 호출한다", async () => {
    mockAuthenticatedSession();
    mockOrders([sampleItem()]);
    renderPage();
    await screen.findByText("ORD-20260828-000123");

    let requestedStatus: string | null = null;
    server.use(
      http.get(`${BASE}/api/orders`, ({ request }) => {
        const url = new URL(request.url);
        requestedStatus = url.searchParams.get("status");
        return HttpResponse.json({
          items: [sampleItem({ status: "COMPLETED", orderId: 1002 })],
          page: { number: 0, size: 20, totalElements: 1 },
        });
      })
    );

    await userEvent.click(screen.getByRole("button", { name: "완료" }));

    await waitFor(() => expect(requestedStatus).toBe("COMPLETED"));
  });

  it("EXPIRED 주문은 기본 목록에 표시하지 않는다(includeExpired 미지정)", async () => {
    mockAuthenticatedSession();
    let requestedUrl: URL | null = null;
    server.use(
      http.get(`${BASE}/api/orders`, ({ request }) => {
        requestedUrl = new URL(request.url);
        return HttpResponse.json({ items: [], page: { number: 0, size: 20, totalElements: 0 } });
      })
    );
    renderPage();

    await screen.findByText("아직 주문이 없어요");
    expect(requestedUrl).not.toBeNull();
    expect((requestedUrl as unknown as URL).searchParams.get("includeExpired")).toBeNull();
  });

  it("비로그인이면 로그인 화면으로 보낸다", async () => {
    mockGuestSession();
    renderPage();

    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/login"));
  });

  it("상태 배지는 09번 표기(색·문구)를 따른다: NO_SHOW는 위험색 노쇼 배지로 보여준다", async () => {
    mockAuthenticatedSession();
    mockOrders([sampleItem({ orderId: 1003, status: "NO_SHOW", pickupNumber: "011" })]);
    renderPage();

    const pickupText = await screen.findByText("픽업 번호 011");
    const card = pickupText.closest("a") as HTMLElement;
    const badge = within(card).getByText("노쇼");
    expect(badge).toHaveClass("bg-danger-weak", "text-danger");
  });
});
