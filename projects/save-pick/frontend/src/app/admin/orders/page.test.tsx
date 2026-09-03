import { describe, expect, it } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AdminAuthProvider } from "@/lib/auth/admin-auth";
import { kstDateString } from "@/lib/server-time";
import AdminOrderListPage from "./page";

const BASE = "http://test.local";

function renderPage() {
  return render(
    <AdminAuthProvider>
      <AdminOrderListPage />
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

function sampleItem(overrides: Record<string, unknown> = {}) {
  return {
    orderId: 1001,
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

/** API-118 시간대 목록. SC-108의 시간대 필터가 이 응답으로 칩을 만든다. */
function mockPickupSlots(date: string, slots: Array<{ slotId: number; startAt: string }>) {
  server.use(
    http.get(`${BASE}/api/admin/pickup-slots`, () =>
      HttpResponse.json({
        date,
        isHoliday: false,
        slots: slots.map((slot) => ({
          slotId: slot.slotId,
          startAt: slot.startAt,
          endAt: slot.startAt,
          capacity: 20,
          reservedCount: 1,
          full: false,
          blocked: false,
          reservationClosed: false,
          itemTotals: [],
        })),
      })
    )
  );
}

function mockOrders(items: unknown[]) {
  server.use(
    http.get(`${BASE}/api/admin/orders`, () =>
      HttpResponse.json({ items, page: { number: 0, size: 20, totalElements: items.length } })
    )
  );
}

describe("SC-108 주문 목록 (관리자)", () => {
  it("기본 상태: 오늘·내일 주문 카드 목록을 보여준다(픽업 번호·상태·고객 이름·결제 금액)", async () => {
    mockAuthenticatedSession();
    let requestedUrl: URL | null = null;
    server.use(
      http.get(`${BASE}/api/admin/orders`, ({ request }) => {
        requestedUrl = new URL(request.url);
        return HttpResponse.json({
          items: [sampleItem()],
          page: { number: 0, size: 20, totalElements: 1 },
        });
      })
    );
    renderPage();

    const card = (await screen.findByText("042")).closest("a") as HTMLElement;
    expect(within(card).getByText("확정")).toBeInTheDocument();
    expect(within(card).getByText("김지현")).toBeInTheDocument();
    expect(within(card).getByText("18,900원")).toBeInTheDocument();
    expect(within(card).getByText(/ORD-20260828-000117/)).toBeInTheDocument();

    // 기본 조회는 날짜·상태 파라미터를 지정하지 않는다(오늘·내일 기본, PENDING·EXPIRED 제외).
    expect(requestedUrl).not.toBeNull();
    expect((requestedUrl as unknown as URL).searchParams.get("pickupDate")).toBeNull();
    expect((requestedUrl as unknown as URL).searchParams.get("status")).toBeNull();
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
    await screen.findByText("조건에 맞는 주문이 없어요");
  });

  it("빈 상태: 조건에 맞는 주문이 없어요 + 필터 초기화 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    mockOrders([sampleItem()]);
    renderPage();
    await screen.findByText("042");

    mockOrders([]);
    await userEvent.click(screen.getByRole("button", { name: "취소" }));

    expect(await screen.findByText("조건에 맞는 주문이 없어요")).toBeInTheDocument();
    expect(screen.getByText(/적용된 필터: 오늘·내일 · 취소/)).toBeInTheDocument();

    mockOrders([sampleItem()]);
    await userEvent.click(screen.getByRole("button", { name: "필터 초기화" }));
    expect(await screen.findByText("042")).toBeInTheDocument();
  });

  it("오류: 불러오지 못했어요 문구와 다시 시도 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/orders`, () =>
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

  it('"오늘" 날짜 필터를 고르면 pickupDate 쿼리로 다시 조회한다', async () => {
    mockAuthenticatedSession();
    mockOrders([sampleItem()]);
    renderPage();
    await screen.findByText("042");

    let requestedUrl: URL | null = null;
    server.use(
      http.get(`${BASE}/api/admin/orders`, ({ request }) => {
        requestedUrl = new URL(request.url);
        return HttpResponse.json({ items: [], page: { number: 0, size: 20, totalElements: 0 } });
      })
    );

    await userEvent.click(screen.getByRole("button", { name: "오늘" }));

    await waitFor(() =>
      expect((requestedUrl as unknown as URL | null)?.searchParams.get("pickupDate")).toBe(
        kstDateString(0)
      )
    );
  });

  it('"취소" 상태 필터를 고르면 status=CANCELED 쿼리로 다시 조회한다', async () => {
    mockAuthenticatedSession();
    mockOrders([sampleItem()]);
    renderPage();
    await screen.findByText("042");

    let requestedUrl: URL | null = null;
    server.use(
      http.get(`${BASE}/api/admin/orders`, ({ request }) => {
        requestedUrl = new URL(request.url);
        return HttpResponse.json({ items: [], page: { number: 0, size: 20, totalElements: 0 } });
      })
    );

    await userEvent.click(screen.getByRole("button", { name: "취소" }));

    await waitFor(() =>
      expect((requestedUrl as unknown as URL | null)?.searchParams.get("status")).toBe("CANCELED")
    );
  });

  it("PENDING·EXPIRED는 상태 필터 목록에 노출하지 않는다(FR-048)", async () => {
    mockAuthenticatedSession();
    mockOrders([sampleItem()]);
    renderPage();
    await screen.findByText("042");

    const filters = screen.getAllByRole("button").map((button) => button.textContent);
    expect(filters).not.toContain("결제 대기");
    expect(filters).not.toContain("선점 만료");
  });

  it("픽업 번호가 없는 결제 실패 주문은 '픽업 번호 없음'으로 표시한다", async () => {
    mockAuthenticatedSession();
    mockOrders([
      sampleItem({
        orderId: 1002,
        orderNo: "ORD-20260828-000055",
        pickupNumber: null,
        pickupStartAt: null,
        pickupEndAt: null,
        noShowDueAt: null,
        status: "FAILED",
        customerName: "한지우",
        totalAmount: 6300,
      }),
    ]);
    renderPage();

    const card = (await screen.findByText("한지우")).closest("a") as HTMLElement;
    expect(within(card).getByText("픽업 번호 없음")).toBeInTheDocument();
    expect(within(card).getByText("결제 실패")).toBeInTheDocument();
  });

  it("시간대 필터: 날짜를 고르면 시간대 칩이 나오고 고른 slotId로 다시 조회한다", async () => {
    mockAuthenticatedSession();
    mockOrders([sampleItem()]);
    const today = kstDateString(0);
    mockPickupSlots(today, [
      { slotId: 341, startAt: `${today}T20:00:00+09:00` },
      { slotId: 342, startAt: `${today}T20:30:00+09:00` },
    ]);
    renderPage();
    await screen.findByText("042");

    // 날짜가 "오늘·내일"인 동안에는 시간대를 고를 수 없다 — 시간대는 날짜에 딸린 값이다.
    expect(screen.queryByRole("button", { name: "시간대 전체" })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "오늘" }));
    expect(await screen.findByRole("button", { name: "20:00" })).toBeInTheDocument();

    let requestedSlotId: string | null = null;
    server.use(
      http.get(`${BASE}/api/admin/orders`, ({ request }) => {
        requestedSlotId = new URL(request.url).searchParams.get("slotId");
        return HttpResponse.json({
          items: [sampleItem()],
          page: { number: 0, size: 20, totalElements: 1 },
        });
      })
    );

    await userEvent.click(screen.getByRole("button", { name: "20:00" }));

    await waitFor(() => expect(requestedSlotId).toBe("341"));
  });

  it("날짜를 바꾸면 이전 시간대 선택을 유지하지 않는다", async () => {
    mockAuthenticatedSession();
    mockOrders([sampleItem()]);
    const today = kstDateString(0);
    mockPickupSlots(today, [{ slotId: 341, startAt: `${today}T20:00:00+09:00` }]);
    renderPage();
    await screen.findByText("042");

    await userEvent.click(screen.getByRole("button", { name: "오늘" }));
    await userEvent.click(await screen.findByRole("button", { name: "20:00" }));

    let requestedSlotId: string | null = "not-called";
    server.use(
      http.get(`${BASE}/api/admin/orders`, ({ request }) => {
        requestedSlotId = new URL(request.url).searchParams.get("slotId");
        return HttpResponse.json({
          items: [sampleItem()],
          page: { number: 0, size: 20, totalElements: 1 },
        });
      })
    );

    await userEvent.click(screen.getByRole("button", { name: "오늘·내일" }));

    // 다른 날짜의 slotId를 그대로 들고 가면 빈 목록이 나온다.
    await waitFor(() => expect(requestedSlotId).toBeNull());
  });

  it("시간대 목록 조회가 실패해도 날짜·상태 필터는 그대로 쓸 수 있다", async () => {
    mockAuthenticatedSession();
    mockOrders([sampleItem()]);
    server.use(
      http.get(`${BASE}/api/admin/pickup-slots`, () =>
        HttpResponse.json(
          { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-28T18:00:00+09:00" },
          { status: 500 }
        )
      )
    );
    renderPage();
    await screen.findByText("042");

    await userEvent.click(screen.getByRole("button", { name: "오늘" }));

    expect(await screen.findByText("042")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "시간대 전체" })).not.toBeInTheDocument();
  });
});
