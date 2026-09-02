import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AdminAuthProvider } from "@/lib/auth/admin-auth";
import { kstDateString } from "@/lib/server-time";
import AdminHomePage from "./page";

const BASE = "http://test.local";
const pushMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock, replace: vi.fn() }),
}));

function renderPage() {
  return render(
    <AdminAuthProvider>
      <AdminHomePage />
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

function ordersResponse(count: number) {
  return HttpResponse.json({
    items: Array.from({ length: count }).map((_, index) => ({
      orderId: 9000 + index,
      orderNo: `ORD-20260828-0000${index}`,
      pickupNumber: "001",
      customerName: "고객",
      status: "CONFIRMED",
      pickupStartAt: "2026-08-28T19:30:00+09:00",
      pickupEndAt: "2026-08-28T20:00:00+09:00",
      noShowDueAt: "2026-08-28T20:30:00+09:00",
      totalAmount: 10000,
      itemCount: 1,
    })),
    page: { number: 0, size: 20, totalElements: count },
  });
}

interface SummaryCounts {
  confirmed?: number;
  ready?: number;
  completed?: number;
  noShow?: number;
  unavailable?: number;
  slots?: unknown[];
}

function mockSummaryApis({
  confirmed = 0,
  ready = 0,
  completed = 0,
  noShow = 0,
  unavailable = 0,
  slots = [],
}: SummaryCounts) {
  server.use(
    http.get(`${BASE}/api/admin/orders`, ({ request }) => {
      const status = new URL(request.url).searchParams.get("status");
      if (status === "CONFIRMED") return ordersResponse(confirmed);
      if (status === "READY") return ordersResponse(ready);
      if (status === "COMPLETED") return ordersResponse(completed);
      if (status === "NO_SHOW") return ordersResponse(noShow);
      return ordersResponse(0);
    }),
    http.get(`${BASE}/api/admin/stocks`, () =>
      HttpResponse.json({
        serverTime: "2026-08-28T14:00:00+09:00",
        items: [],
        page: { number: 0, size: 20, totalElements: unavailable },
      })
    ),
    http.get(`${BASE}/api/admin/pickup-slots`, () =>
      HttpResponse.json({ date: kstDateString(0), isHoliday: false, slots })
    )
  );
}

describe("SC-102 관리자 홈", () => {
  it("기본 상태: 오늘 요약 5개 값과 다음 픽업 시간대, 관리 메뉴 8개를 보여준다", async () => {
    mockAuthenticatedSession();
    mockSummaryApis({
      confirmed: 12,
      ready: 4,
      completed: 21,
      noShow: 2,
      unavailable: 3,
      slots: [
        {
          slotId: 341,
          startAt: "2099-01-01T20:00:00+09:00",
          endAt: "2099-01-01T20:30:00+09:00",
          capacity: 20,
          reservedCount: 4,
          full: false,
          blocked: false,
          reservationClosed: false,
          itemTotals: [],
        },
      ],
    });
    renderPage();

    expect(await screen.findByText("12")).toBeInTheDocument();
    expect(screen.getByText("4")).toBeInTheDocument();
    expect(screen.getByText("21")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText("20:00~20:30 · 예약 4/20건")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "픽업 현황 보기" })).toHaveAttribute(
      "href",
      "/admin/pickup-status"
    );

    const menuLabels = [
      "상품 관리",
      "재고 관리",
      "재고 이력",
      "할인 정책",
      "주문 관리",
      "픽업 현황",
      "노쇼",
      "운영 설정",
    ];
    menuLabels.forEach((label) => {
      expect(screen.getByRole("link", { name: new RegExp(`^${label}›$`) })).toBeInTheDocument();
    });
    expect(screen.getByRole("link", { name: /^노쇼›$/ })).toHaveAttribute("href", "/admin/no-shows");
    expect(screen.getByRole("link", { name: /^재고 관리›$/ })).toHaveAttribute(
      "href",
      "/admin/stocks"
    );
  });

  it("로딩 상태: 요약 카드 스켈레톤을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/orders`, async () => {
        await new Promise((resolve) => setTimeout(resolve, 30));
        return ordersResponse(0);
      }),
      http.get(`${BASE}/api/admin/stocks`, () =>
        HttpResponse.json({
          serverTime: "2026-08-28T14:00:00+09:00",
          items: [],
          page: { number: 0, size: 20, totalElements: 0 },
        })
      ),
      http.get(`${BASE}/api/admin/pickup-slots`, () =>
        HttpResponse.json({ date: kstDateString(0), isHoliday: false, slots: [] })
      )
    );
    renderPage();

    expect(screen.getAllByRole("status", { name: "불러오는 중" }).length).toBeGreaterThan(0);
    await screen.findByText("오늘 들어온 주문이 없어요");
  });

  it("빈 상태: 오늘 들어온 주문이 없으면 안내 문구를 보여주고 메뉴는 그대로 쓸 수 있다", async () => {
    mockAuthenticatedSession();
    mockSummaryApis({ unavailable: 0, slots: [] });
    renderPage();

    expect(await screen.findByText("오늘 들어온 주문이 없어요")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /^상품 관리›$/ })).toHaveAttribute(
      "href",
      "/admin/products"
    );
  });

  it("오류: 요약을 불러오지 못했어요 문구와 다시 시도 버튼을 보여주고 메뉴는 그대로 쓸 수 있다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/orders`, () =>
        HttpResponse.json(
          { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-28T18:00:00+09:00" },
          { status: 500 }
        )
      ),
      http.get(`${BASE}/api/admin/stocks`, () =>
        HttpResponse.json({
          serverTime: "2026-08-28T14:00:00+09:00",
          items: [],
          page: { number: 0, size: 20, totalElements: 0 },
        })
      ),
      http.get(`${BASE}/api/admin/pickup-slots`, () =>
        HttpResponse.json({ date: kstDateString(0), isHoliday: false, slots: [] })
      )
    );
    renderPage();

    expect(await screen.findByText("요약을 불러오지 못했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /^운영 설정›$/ })).toHaveAttribute(
      "href",
      "/admin/settings"
    );
  });

  it("픽업 번호 빠른 입력: 입력한 번호를 조회 화면으로 함께 넘긴다", async () => {
    mockAuthenticatedSession();
    mockSummaryApis({});
    renderPage();
    await screen.findByText("오늘 들어온 주문이 없어요");

    await userEvent.type(screen.getByLabelText("픽업 번호 3자리"), "042");
    await userEvent.click(screen.getByRole("button", { name: "조회" }));

    expect(pushMock).toHaveBeenCalledWith("/admin/pickup-lookup?number=042");
  });
});
