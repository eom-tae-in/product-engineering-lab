import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AdminAuthProvider } from "@/lib/auth/admin-auth";
import { setServerTimeOffsetMs } from "@/lib/server-time";
import AdminOrderDetailPage from "./page";

const BASE = "http://test.local";

async function renderPage(id = "1001") {
  const page = await AdminOrderDetailPage({
    params: Promise.resolve({ id }),
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

function sampleDetail(overrides: Record<string, unknown> = {}) {
  return {
    orderId: 1001,
    orderNo: "ORD-20260828-000117",
    status: "CONFIRMED",
    pickupNumber: "042",
    pickupStartAt: "2026-08-28T20:00:00+09:00",
    pickupEndAt: "2026-08-28T20:30:00+09:00",
    customer: { name: "김지현", phone: "01098765432" },
    items: [
      { productId: 12, name: "삼겹살 500g", quantity: 2, unitPrice: 8400, lineAmount: 16800, productClosingAt: "2026-08-31T21:00:00+09:00" },
      { productId: 13, name: "대파 1단", quantity: 1, unitPrice: 2100, lineAmount: 2100, productClosingAt: "2026-08-31T21:00:00+09:00" },
    ],
    totalAmount: 18900,
    paymentAttempts: [
      {
        attemptNo: 1,
        status: "FAILED",
        failureReason: "DECLINED",
        requestedAt: "2026-08-28T18:23:11+09:00",
        resolvedAt: "2026-08-28T18:23:12+09:00",
      },
      {
        attemptNo: 2,
        status: "SUCCEEDED",
        requestedAt: "2026-08-28T18:24:02+09:00",
        resolvedAt: "2026-08-28T18:24:03+09:00",
      },
    ],
    statusHistory: [
      { fromStatus: null, toStatus: "PENDING", actorType: "CUSTOMER", occurredAt: "2026-08-28T18:23:00+09:00" },
      { fromStatus: "PENDING", toStatus: "CONFIRMED", actorType: "CUSTOMER", occurredAt: "2026-08-28T18:24:00+09:00" },
    ],
    availableActions: ["READY", "COMPLETE", "CANCEL"],
    ...overrides,
  };
}

function mockDetail(detail: Record<string, unknown>) {
  server.use(http.get(`${BASE}/api/admin/orders/1001`, () => HttpResponse.json(detail)));
}

describe("SC-110 주문 상세 (관리자)", () => {
  // 취소 시트의 재고 복구 안내가 상품 마감 시각과 "지금"의 비교로 갈리므로(BR-019)
  // 픽스처 시점으로 시각을 고정한다. 고정하지 않으면 실행 날짜에 따라 문구가 바뀐다.
  beforeEach(() => {
    setServerTimeOffsetMs(0);
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date("2026-08-28T09:00:00.000Z")); // KST 2026-08-28 18:00
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("기본 상태(CONFIRMED): 주문 정보와 세 가지 액션을 모두 보여준다", async () => {
    mockAuthenticatedSession();
    mockDetail(sampleDetail());
    await renderPage();

    expect(await screen.findByText("042")).toBeInTheDocument();
    expect(screen.getAllByText("확정").length).toBeGreaterThan(0);
    expect(screen.getByText("김지현")).toBeInTheDocument();
    expect(screen.getByText("01098765432")).toBeInTheDocument();
    expect(screen.getByText("16,800원")).toBeInTheDocument();
    expect(screen.getByText("18,900원")).toBeInTheDocument();
    expect(screen.getByText("1회 · 실패")).toBeInTheDocument();
    expect(screen.getByText("2회 · 성공")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "준비 완료" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "픽업 완료" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "관리자 취소" })).toBeInTheDocument();
  });

  it("로딩 상태: 카드 스켈레톤을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/orders/1001`, async () => {
        await new Promise((resolve) => setTimeout(resolve, 30));
        return HttpResponse.json(sampleDetail());
      })
    );
    await renderPage();

    expect(screen.getAllByRole("status", { name: "불러오는 중" }).length).toBeGreaterThan(0);
    await screen.findByText("042");
  });

  it("오류: 불러오지 못했어요 문구와 다시 시도 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/orders/1001`, () =>
        HttpResponse.json(
          { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-28T18:00:00+09:00" },
          { status: 500 }
        )
      )
    );
    await renderPage();

    expect(await screen.findByText("불러오지 못했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("준비 완료 액션: 성공하면 READY로 바뀌고 준비 완료 버튼이 사라진다", async () => {
    mockAuthenticatedSession();
    mockDetail(sampleDetail());
    await renderPage();
    await screen.findByText("042");

    server.use(
      http.post(`${BASE}/api/admin/orders/1001/ready`, () =>
        HttpResponse.json({
          orderId: 1001,
          status: "READY",
          readyAt: "2026-08-28T19:52:00+09:00",
          stockChanged: false,
        })
      )
    );
    mockDetail(sampleDetail({ status: "READY", availableActions: ["COMPLETE", "CANCEL"] }));

    await userEvent.click(screen.getByRole("button", { name: "준비 완료" }));

    expect(await screen.findByText("준비 완료")).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole("button", { name: "준비 완료" })).not.toBeInTheDocument()
    );
  });

  it("준비 완료 액션 실패: CONFIRMED 주문만 준비 완료로 바꿀 수 있어요 안내를 보여준다", async () => {
    mockAuthenticatedSession();
    mockDetail(sampleDetail());
    await renderPage();
    await screen.findByText("042");

    server.use(
      http.post(`${BASE}/api/admin/orders/1001/ready`, () =>
        HttpResponse.json(
          {
            code: "INVALID_ORDER_STATUS",
            message: "이미 준비 완료된 주문이에요",
            serverTime: "2026-08-28T18:00:00+09:00",
            details: { currentStatus: "READY" },
          },
          { status: 409 }
        )
      )
    );

    await userEvent.click(screen.getByRole("button", { name: "준비 완료" }));

    await waitFor(() =>
      expect(screen.getByRole("dialog").textContent).toContain(
        "CONFIRMED 주문만 준비 완료로 바꿀 수 있어요"
      )
    );
  });

  it("픽업 완료 액션: 성공하면 상단 안내 띠와 함께 액션이 사라진다", async () => {
    mockAuthenticatedSession();
    mockDetail(sampleDetail());
    await renderPage();
    await screen.findByText("042");

    server.use(
      http.post(`${BASE}/api/admin/orders/1001/complete`, () =>
        HttpResponse.json({
          orderId: 1001,
          status: "COMPLETED",
          completedAt: "2026-08-28T20:12:00+09:00",
          stockChanged: false,
        })
      )
    );
    mockDetail(
      sampleDetail({
        status: "COMPLETED",
        availableActions: [],
        statusHistory: [
          ...sampleDetail().statusHistory,
          { fromStatus: "CONFIRMED", toStatus: "COMPLETED", actorType: "ADMIN", occurredAt: "2026-08-28T20:12:00+09:00" },
        ],
      })
    );

    await userEvent.click(screen.getByRole("button", { name: "픽업 완료" }));

    expect(await screen.findByText("20:12에 픽업 완료된 주문이에요")).toBeInTheDocument();
    expect(screen.getByText("더 이상 바꿀 수 있는 상태가 없어요")).toBeInTheDocument();
  });

  it("픽업 완료 액션 실패(중복): 이미 완료 처리된 주문이에요 안내를 보여준다", async () => {
    mockAuthenticatedSession();
    mockDetail(sampleDetail());
    await renderPage();
    await screen.findByText("042");

    server.use(
      http.post(`${BASE}/api/admin/orders/1001/complete`, () =>
        HttpResponse.json(
          {
            code: "INVALID_ORDER_STATUS",
            message: "이미 완료 처리됐어요",
            serverTime: "2026-08-28T18:00:00+09:00",
            details: { currentStatus: "COMPLETED" },
          },
          { status: 409 }
        )
      )
    );

    await userEvent.click(screen.getByRole("button", { name: "픽업 완료" }));

    await waitFor(() =>
      expect(screen.getByRole("dialog").textContent).toContain("이미 완료 처리된 주문이에요")
    );
  });

  it("픽업 완료 액션 실패(노쇼 이후): 노쇼로 전환된 주문은 완료 처리할 수 없어요 안내를 보여준다", async () => {
    mockAuthenticatedSession();
    mockDetail(sampleDetail());
    await renderPage();
    await screen.findByText("042");

    server.use(
      http.post(`${BASE}/api/admin/orders/1001/complete`, () =>
        HttpResponse.json(
          {
            code: "INVALID_ORDER_STATUS",
            message: "노쇼 처리됨",
            serverTime: "2026-08-28T18:00:00+09:00",
            details: { currentStatus: "NO_SHOW" },
          },
          { status: 409 }
        )
      )
    );

    await userEvent.click(screen.getByRole("button", { name: "픽업 완료" }));

    await waitFor(() =>
      expect(screen.getByRole("dialog").textContent).toContain(
        "노쇼로 전환된 주문은 완료 처리할 수 없어요"
      )
    );
  });

  it("취소 시트: 사유 없이 제출하면 취소 사유를 입력해주세요를 보여주고 실행하지 않는다", async () => {
    mockAuthenticatedSession();
    mockDetail(sampleDetail());
    let cancelCalled = false;
    server.use(
      http.post(`${BASE}/api/admin/orders/1001/cancel`, () => {
        cancelCalled = true;
        return HttpResponse.json(sampleDetail({ status: "CANCELED" }));
      })
    );
    await renderPage();
    await screen.findByText("042");

    await userEvent.click(screen.getByRole("button", { name: "관리자 취소" }));
    const dialog = screen.getByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "취소 처리하기" }));

    expect(await screen.findByText("취소 사유를 입력해주세요")).toBeInTheDocument();
    expect(cancelCalled).toBe(false);
  });

  it("취소 시트: 사유를 입력하고 제출하면 취소 처리 후 액션이 사라진다", async () => {
    mockAuthenticatedSession();
    mockDetail(sampleDetail());
    let requestedBody: unknown = null;
    server.use(
      http.post(`${BASE}/api/admin/orders/1001/cancel`, async ({ request }) => {
        requestedBody = await request.json();
        return HttpResponse.json({
          orderId: 1001,
          status: "CANCELED",
          canceledBy: "ADMIN",
          cancelReason: "상품 손상으로 준비 불가",
          canceledAt: "2026-08-28T19:45:00+09:00",
          slotReleased: true,
          stockResults: [{ productId: 12, quantity: 2, restored: true, reason: "CANCEL_RESTORE" }],
        });
      })
    );
    await renderPage();
    await screen.findByText("042");

    await userEvent.click(screen.getByRole("button", { name: "관리자 취소" }));
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText("취소하면 수량 3개가 판매 가능 재고로 돌아가요")).toBeInTheDocument();

    await userEvent.type(within(dialog).getByLabelText("취소 사유 (필수)"), "상품 손상으로 준비 불가");

    mockDetail(
      sampleDetail({
        status: "CANCELED",
        availableActions: [],
        statusHistory: [
          ...sampleDetail().statusHistory,
          { fromStatus: "CONFIRMED", toStatus: "CANCELED", actorType: "ADMIN", occurredAt: "2026-08-28T19:45:00+09:00" },
        ],
      })
    );

    await userEvent.click(within(dialog).getByRole("button", { name: "취소 처리하기" }));

    expect(await screen.findByText("더 이상 바꿀 수 있는 상태가 없어요")).toBeInTheDocument();
    expect(requestedBody).toEqual({ reason: "상품 손상으로 준비 불가" });
  });

  /**
   * API-117의 CANCEL_NOT_ALLOWED는 COMPLETED·NO_SHOW·CANCELED를 한 코드로 묶고
   * `details.currentStatus`도 담지 않는다. 화면은 상세를 다시 읽어 실제 상태로 문구를
   * 고른다 — 이미 취소된 주문에 "픽업 완료된 주문"이라고 답하지 않기 위해서다.
   */
  function mockCancelConflictThenStatus(refreshedStatus: string) {
    let canceled = false;
    server.use(
      http.get(`${BASE}/api/admin/orders/1001`, () =>
        HttpResponse.json(
          canceled
            ? sampleDetail({ status: refreshedStatus, availableActions: [] })
            : sampleDetail()
        )
      ),
      http.post(`${BASE}/api/admin/orders/1001/cancel`, () => {
        canceled = true;
        return HttpResponse.json(
          {
            code: "CANCEL_NOT_ALLOWED",
            message: "취소할 수 없어요",
            serverTime: "2026-08-28T18:00:00+09:00",
          },
          { status: 409 }
        );
      })
    );
  }

  async function submitCancel() {
    await userEvent.click(screen.getByRole("button", { name: "관리자 취소" }));
    const dialog = screen.getByRole("dialog");
    await userEvent.type(within(dialog).getByLabelText("취소 사유 (필수)"), "손상");
    await userEvent.click(within(dialog).getByRole("button", { name: "취소 처리하기" }));
  }

  it("취소 실패(그 사이 픽업 완료): 픽업 완료된 주문은 취소할 수 없어요 안내를 보여준다", async () => {
    mockAuthenticatedSession();
    mockCancelConflictThenStatus("COMPLETED");
    await renderPage();
    await screen.findByText("042");

    await submitCancel();

    await waitFor(() =>
      expect(screen.getByRole("dialog").textContent).toContain("픽업 완료된 주문은 취소할 수 없어요")
    );
  });

  it("취소 실패(그 사이 취소됨): 픽업 완료가 아니라 이미 취소된 주문이라고 알린다", async () => {
    mockAuthenticatedSession();
    mockCancelConflictThenStatus("CANCELED");
    await renderPage();
    await screen.findByText("042");

    await submitCancel();

    await waitFor(() =>
      expect(screen.getByRole("dialog").textContent).toContain("이미 취소된 주문이에요")
    );
    expect(screen.getByRole("dialog").textContent).not.toContain("픽업 완료된 주문");
  });

  it("노쇼 주문: 상단 안내 띠와 액션 없음 안내를 보여준다", async () => {
    mockAuthenticatedSession();
    mockDetail(
      sampleDetail({
        status: "NO_SHOW",
        availableActions: [],
        statusHistory: [
          ...sampleDetail().statusHistory,
          { fromStatus: "CONFIRMED", toStatus: "NO_SHOW", actorType: "SYSTEM", occurredAt: "2026-08-28T21:00:00+09:00" },
        ],
      })
    );
    await renderPage();

    expect(await screen.findByText("21:00에 노쇼로 전환된 주문이에요")).toBeInTheDocument();
    expect(screen.getByText("더 이상 바꿀 수 있는 상태가 없어요")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "관리자 취소" })).not.toBeInTheDocument();
  });

  it("취소 시트: 상품 마감 이후면 재고로 되돌리지 않는다고 안내한다", async () => {
    mockAuthenticatedSession();
    // BR-019 — 마감이 지난 품목은 취소해도 재고로 돌아오지 않고 폐기로 기록된다.
    mockDetail(
      sampleDetail({
        items: [
          {
            productId: 12,
            name: "삼겹살 500g",
            quantity: 2,
            unitPrice: 8400,
            lineAmount: 16800,
            productClosingAt: "2020-01-01T21:00:00+09:00",
          },
        ],
      })
    );
    await renderPage();
    await screen.findByText("042");

    await userEvent.click(screen.getByRole("button", { name: "관리자 취소" }));
    const dialog = screen.getByRole("dialog");

    expect(
      within(dialog).getByText("마감 시각이 지나 재고로 되돌리지 않고 폐기로 기록해요")
    ).toBeInTheDocument();
  });
});
