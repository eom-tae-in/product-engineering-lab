import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AuthProvider } from "@/lib/auth/customer-auth";
import OrderCancelPage from "./page";

const BASE = "http://test.local";
const pushMock = vi.fn();
const replaceMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock, replace: replaceMock }),
}));

async function renderPage() {
  const page = await OrderCancelPage({
    params: Promise.resolve({ id: "1001" }),
    searchParams: Promise.resolve({}),
  });
  return render(<AuthProvider>{page}</AuthProvider>);
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

function sampleDetail(overrides: Record<string, unknown> = {}) {
  return {
    serverTime: "2026-08-31T10:05:00+09:00",
    orderId: 1001,
    orderNo: "ORD-20260831-000001",
    status: "CONFIRMED",
    orderedAt: "2026-08-31T10:01:00+09:00",
    items: [
      {
        productId: 12,
        name: "국내산 삼겹살 300g",
        quantity: 2,
        unitPrice: 6000,
        lineAmount: 12000,
      },
    ],
    totalAmount: 12000,
    pickupNumber: "017",
    pickupStartAt: "2026-08-31T19:30:00+09:00",
    pickupEndAt: "2026-08-31T20:00:00+09:00",
    noShowDueAt: "2026-08-31T20:30:00+09:00",
    cancelable: true,
    cancelableUntil: "2026-08-31T18:30:00+09:00",
    cancelUnavailableReason: null,
    canceledBy: null,
    cancelReason: null,
    store: {
      name: "savePick 성수점",
      address: "서울 성동구 성수이로 10",
      phone: "02-1234-5678",
    },
    statusHistory: [
      { toStatus: "CONFIRMED", actorType: "CUSTOMER", occurredAt: "2026-08-31T10:01:00+09:00" },
    ],
    ...overrides,
  };
}

function mockDetail(body: Record<string, unknown>) {
  server.use(http.get(`${BASE}/api/orders/1001`, () => HttpResponse.json(body)));
}

function mockCancelError(code: string, status: number, details?: Record<string, unknown>) {
  server.use(
    http.post(`${BASE}/api/orders/1001/cancel`, () =>
      HttpResponse.json(
        { code, message: "취소할 수 없어요.", serverTime: "2026-08-31T10:00:00+09:00", details },
        { status }
      )
    )
  );
}

async function renderCancelable() {
  mockAuthenticatedSession();
  mockDetail(sampleDetail());
  await renderPage();
  await screen.findByRole("button", { name: "전체 취소하기" });
}

describe("SC-011 주문 취소 확인", () => {
  it("기본 상태: 취소할 주문 요약과 전체 취소 안내를 보여준다", async () => {
    await renderCancelable();

    expect(screen.getByText("017")).toBeInTheDocument();
    expect(screen.getByText("2026-08-31 19:30~20:00")).toBeInTheDocument();
    expect(screen.getByText("12,000원")).toBeInTheDocument();
    expect(screen.getByText("ORD-20260831-000001")).toBeInTheDocument();
    expect(
      screen.getByText("부분 취소는 할 수 없어요. 주문 전체가 취소돼요")
    ).toBeInTheDocument();
    expect(screen.getByText("취소한 수량은 바로 다시 판매돼요")).toBeInTheDocument();
  });

  it("전체 취소하기: confirmed=true로 요청하고 주문 상세로 돌아간다", async () => {
    await renderCancelable();
    let requestBody: unknown = null;
    server.use(
      http.post(`${BASE}/api/orders/1001/cancel`, async ({ request }) => {
        requestBody = await request.json();
        return HttpResponse.json({
          orderId: 1001,
          status: "CANCELED",
          canceledAt: "2026-08-31T11:00:00+09:00",
          canceledBy: "CUSTOMER",
          slotReleased: true,
          stockResults: [
            { productId: 12, quantity: 2, restored: true, reason: "CANCEL_RESTORE" },
          ],
        });
      })
    );

    await userEvent.click(screen.getByRole("button", { name: "전체 취소하기" }));

    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/orders/1001"));
    expect(requestBody).toEqual({ confirmed: true });
  });

  it("로딩(버튼): 취소 처리 중에는 문구가 바뀌고 중복 탭을 막는다", async () => {
    await renderCancelable();
    let resolveCancel!: (response: Response) => void;
    server.use(
      http.post(`${BASE}/api/orders/1001/cancel`, () => {
        return new Promise<Response>((resolve) => {
          resolveCancel = resolve;
        });
      })
    );

    await userEvent.click(screen.getByRole("button", { name: "전체 취소하기" }));

    expect(screen.getByRole("button", { name: "취소 처리 중이에요" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "취소하지 않기" })).toBeDisabled();

    resolveCancel(
      HttpResponse.json({
        orderId: 1001,
        status: "CANCELED",
        canceledAt: "2026-08-31T11:00:00+09:00",
        canceledBy: "CUSTOMER",
        slotReleased: true,
        stockResults: [],
      })
    );
    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/orders/1001"));
  });

  it("취소하지 않기: 주문 상세로 돌아간다", async () => {
    await renderCancelable();

    await userEvent.click(screen.getByRole("button", { name: "취소하지 않기" }));

    expect(pushMock).toHaveBeenCalledWith("/orders/1001");
  });

  it("진입 시 이미 취소 마감이 지났으면 요청하지 않고 안내만 보여준다", async () => {
    mockAuthenticatedSession();
    mockDetail(
      sampleDetail({ cancelable: false, cancelUnavailableReason: "CANCEL_DEADLINE_PASSED" })
    );

    await renderPage();

    expect(
      await screen.findByText("방금 취소 가능 시각이 지났어요 (18:30)")
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "전체 취소하기" })).not.toBeInTheDocument();
  });

  it("진입 시 이미 픽업 완료면 상태 불일치 문구를 보여준다", async () => {
    mockAuthenticatedSession();
    mockDetail(
      sampleDetail({
        status: "COMPLETED",
        cancelable: false,
        cancelUnavailableReason: "ALREADY_COMPLETED",
      })
    );

    await renderPage();

    expect(
      await screen.findByText("이미 처리된 주문이에요. 현재 상태는 픽업 완료예요")
    ).toBeInTheDocument();
  });

  it("오류(취소 마감 초과): 지난 시각을 담은 시트를 보여준다", async () => {
    await renderCancelable();
    mockCancelError("CANCEL_DEADLINE_PASSED", 409, {
      cancelableUntil: "2026-08-31T18:30:00+09:00",
    });

    await userEvent.click(screen.getByRole("button", { name: "전체 취소하기" }));

    expect(
      await screen.findByText("방금 취소 가능 시각이 지났어요 (18:30)")
    ).toBeInTheDocument();
    // 시트 밖 딤도 aria-label이 "닫기"라 시트 안으로 좁혀서 찾는다.
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "닫기" }));
    expect(pushMock).toHaveBeenCalledWith("/orders/1001");
  });

  it("오류(상태 불일치·취소됨): 이미 취소된 주문 문구를 보여준다", async () => {
    await renderCancelable();
    mockCancelError("CANCEL_NOT_ALLOWED", 409, { currentStatus: "CANCELED" });

    await userEvent.click(screen.getByRole("button", { name: "전체 취소하기" }));

    expect(await screen.findByText("이미 취소된 주문이에요")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "주문 상세로" }));
    expect(pushMock).toHaveBeenCalledWith("/orders/1001");
  });

  it("오류(상태 불일치·노쇼): 이미 노쇼로 처리된 주문 문구를 보여준다", async () => {
    await renderCancelable();
    mockCancelError("CANCEL_NOT_ALLOWED", 409, { currentStatus: "NO_SHOW" });

    await userEvent.click(screen.getByRole("button", { name: "전체 취소하기" }));

    expect(await screen.findByText("이미 노쇼로 처리된 주문이에요")).toBeInTheDocument();
  });

  it("오류(통신): 다시 시도하면 취소가 성공한다", async () => {
    await renderCancelable();
    let attempt = 0;
    server.use(
      http.post(`${BASE}/api/orders/1001/cancel`, () => {
        attempt += 1;
        if (attempt === 1) {
          return HttpResponse.json(
            { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T10:00:00+09:00" },
            { status: 500 }
          );
        }
        return HttpResponse.json({
          orderId: 1001,
          status: "CANCELED",
          canceledAt: "2026-08-31T11:00:00+09:00",
          canceledBy: "CUSTOMER",
          slotReleased: true,
          stockResults: [],
        });
      })
    );

    await userEvent.click(screen.getByRole("button", { name: "전체 취소하기" }));

    await screen.findByText("취소하지 못했어요");
    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/orders/1001"));
  });

  it("오류(주문 조회 실패): 다시 시도 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/orders/1001`, () =>
        HttpResponse.json(
          { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T10:00:00+09:00" },
          { status: 500 }
        )
      )
    );

    await renderPage();

    expect(await screen.findByText("주문 정보를 불러오지 못했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("비로그인이면 로그인 화면으로 보낸다", async () => {
    mockGuestSession();

    await renderPage();

    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/login"));
  });
});
