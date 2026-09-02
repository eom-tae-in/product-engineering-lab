import { describe, expect, it } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AdminAuthProvider } from "@/lib/auth/admin-auth";
import { kstDateString } from "@/lib/server-time";
import AdminPickupStatusPage from "./page";

const BASE = "http://test.local";

function renderPage() {
  return render(
    <AdminAuthProvider>
      <AdminPickupStatusPage />
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

function sampleSlot(overrides: Record<string, unknown> = {}) {
  return {
    slotId: 341,
    // 실제 실행 시각과 무관하게 항상 "미래"로 판정되도록 먼 미래 값을 쓴다.
    startAt: "2099-01-01T19:30:00+09:00",
    endAt: "2099-01-01T20:00:00+09:00",
    capacity: 20,
    reservedCount: 4,
    full: false,
    blocked: false,
    reservationClosed: false,
    itemTotals: [{ productId: 12, name: "국내산 삼겹살 300g", quantity: 7 }],
    ...overrides,
  };
}

function mockSlots(slots: unknown[], date = kstDateString(0)) {
  server.use(
    http.get(`${BASE}/api/admin/pickup-slots`, () =>
      HttpResponse.json({ date, isHoliday: false, slots })
    )
  );
}

describe("SC-111 시간대별 픽업 현황", () => {
  it("기본 상태: 시간대·예약 건수·품목 수를 보여준다", async () => {
    mockAuthenticatedSession();
    let requestedUrl: URL | null = null;
    server.use(
      http.get(`${BASE}/api/admin/pickup-slots`, ({ request }) => {
        requestedUrl = new URL(request.url);
        return HttpResponse.json({
          date: kstDateString(0),
          isHoliday: false,
          slots: [sampleSlot()],
        });
      })
    );
    renderPage();

    expect(await screen.findByText("19:30~20:00")).toBeInTheDocument();
    expect(screen.getByText("4/20건")).toBeInTheDocument();
    expect(screen.getByText("품목 7개")).toBeInTheDocument();
    expect((requestedUrl as unknown as URL | null)?.searchParams.get("date")).toBe(
      kstDateString(0)
    );
  });

  it("로딩 상태: 행 스켈레톤을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/pickup-slots`, async () => {
        await new Promise((resolve) => setTimeout(resolve, 30));
        return HttpResponse.json({ date: kstDateString(0), isHoliday: false, slots: [] });
      })
    );
    renderPage();

    expect(screen.getAllByRole("status", { name: "불러오는 중" }).length).toBeGreaterThan(0);
    await screen.findByText("이 날짜에 예약된 픽업이 없어요");
  });

  it("빈 상태: 이 날짜에 예약된 픽업이 없어요 + 날짜 전환 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    mockSlots([sampleSlot({ reservedCount: 0 })]);
    renderPage();
    await screen.findByText("이 날짜에 예약된 픽업이 없어요");

    let requestedUrl: URL | null = null;
    server.use(
      http.get(`${BASE}/api/admin/pickup-slots`, ({ request }) => {
        requestedUrl = new URL(request.url);
        return HttpResponse.json({ date: kstDateString(1), isHoliday: false, slots: [] });
      })
    );
    await userEvent.click(screen.getByRole("button", { name: "내일 " + monthDay(1) + " 보기" }));

    await waitFor(() =>
      expect((requestedUrl as unknown as URL | null)?.searchParams.get("date")).toBe(
        kstDateString(1)
      )
    );
  });

  it("오류: 불러오지 못했어요 문구와 다시 시도 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/pickup-slots`, () =>
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

  it("정원 도달: 정원 마감 배지를 보여준다", async () => {
    mockAuthenticatedSession();
    mockSlots([sampleSlot({ reservedCount: 20, capacity: 20, full: true })]);
    renderPage();

    expect(await screen.findByText("20/20 정원 마감")).toBeInTheDocument();
  });

  it("차단 시간대: 운영 중단 배지와 해제 버튼을 보여주고, 해제하면 배지가 사라진다", async () => {
    mockAuthenticatedSession();
    mockSlots([sampleSlot({ blocked: true })]);
    let patchBody: unknown = null;
    server.use(
      http.patch(`${BASE}/api/admin/pickup-slots/341`, async ({ request }) => {
        patchBody = await request.json();
        return HttpResponse.json({
          slotId: 341,
          capacity: 20,
          reservedCount: 4,
          blocked: false,
          overCapacity: false,
          keptOrderCount: 4,
        });
      })
    );
    renderPage();

    expect(await screen.findByText("운영 중단")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "해제" }));

    await waitFor(() => expect(screen.queryByText("운영 중단")).not.toBeInTheDocument());
    expect(patchBody).toEqual({ blocked: false });
  });

  it("지난 시간대: 종료 배지를 보여주고 펼치기·차단 버튼을 두지 않는다", async () => {
    mockAuthenticatedSession();
    mockSlots([
      sampleSlot({
        slotId: 99,
        startAt: "2020-01-01T18:30:00+09:00",
        endAt: "2020-01-01T19:00:00+09:00",
      }),
    ]);
    renderPage();

    expect(await screen.findByText("종료")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "펼치기" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "차단" })).not.toBeInTheDocument();
  });

  it("펼치기: 시간대 주문 목록(픽업 번호·이름·상태)을 보여주고 확정 주문을 일괄 준비 완료 처리한다", async () => {
    mockAuthenticatedSession();
    mockSlots([sampleSlot()]);
    let requestedSlotId: string | null = null;
    server.use(
      http.get(`${BASE}/api/admin/orders`, ({ request }) => {
        requestedSlotId = new URL(request.url).searchParams.get("slotId");
        return HttpResponse.json({
          items: [
            {
              orderId: 1001,
              orderNo: "ORD-20260828-000117",
              pickupNumber: "042",
              customerName: "김지현",
              status: "CONFIRMED",
              pickupStartAt: "2099-01-01T19:30:00+09:00",
              pickupEndAt: "2099-01-01T20:00:00+09:00",
              noShowDueAt: "2099-01-01T20:30:00+09:00",
              totalAmount: 18900,
              itemCount: 2,
            },
          ],
          page: { number: 0, size: 20, totalElements: 1 },
        });
      }),
      http.post(`${BASE}/api/admin/orders/1001/ready`, () =>
        HttpResponse.json({
          orderId: 1001,
          status: "READY",
          readyAt: "2099-01-01T19:52:00+09:00",
          stockChanged: false,
        })
      )
    );
    renderPage();
    await screen.findByText("19:30~20:00");

    await userEvent.click(screen.getByRole("button", { name: "펼치기" }));

    expect(await screen.findByText("042 김지현")).toBeInTheDocument();
    expect(requestedSlotId).toBe("341");

    server.use(
      http.get(`${BASE}/api/admin/orders`, () =>
        HttpResponse.json({
          items: [
            {
              orderId: 1001,
              orderNo: "ORD-20260828-000117",
              pickupNumber: "042",
              customerName: "김지현",
              status: "READY",
              pickupStartAt: "2099-01-01T19:30:00+09:00",
              pickupEndAt: "2099-01-01T20:00:00+09:00",
              noShowDueAt: "2099-01-01T20:30:00+09:00",
              totalAmount: 18900,
              itemCount: 2,
            },
          ],
          page: { number: 0, size: 20, totalElements: 1 },
        })
      )
    );

    await userEvent.click(screen.getByRole("button", { name: "확정 주문 일괄 준비 완료" }));

    await waitFor(() =>
      expect(
        screen.queryByRole("button", { name: "확정 주문 일괄 준비 완료" })
      ).not.toBeInTheDocument()
    );
  });

  it("차단 확인 시트: 차단하기를 누르면 blocked:true로 요청하고 운영 중단 배지를 보여준다", async () => {
    mockAuthenticatedSession();
    mockSlots([sampleSlot()]);
    let patchBody: unknown = null;
    server.use(
      http.patch(`${BASE}/api/admin/pickup-slots/341`, async ({ request }) => {
        patchBody = await request.json();
        return HttpResponse.json({
          slotId: 341,
          capacity: 20,
          reservedCount: 4,
          blocked: true,
          overCapacity: false,
          keptOrderCount: 4,
        });
      })
    );
    renderPage();
    await screen.findByText("19:30~20:00");

    await userEvent.click(screen.getByRole("button", { name: "차단" }));
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText("19:30~20:00 시간대를 차단할까요?")).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "차단하기" }));

    await waitFor(() => expect(screen.getByText("운영 중단")).toBeInTheDocument());
    expect(patchBody).toEqual({ blocked: true });
  });
});

function monthDay(daysFromToday: number): string {
  const [, month, day] = kstDateString(daysFromToday).split("-");
  return `${Number(month)}/${Number(day)}`;
}
