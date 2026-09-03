import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AuthProvider } from "@/lib/auth/customer-auth";
import OrderDetailPage from "./page";

const BASE = "http://test.local";
const pushMock = vi.fn();
const replaceMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock, replace: replaceMock }),
}));

async function renderPage(justConfirmed?: string) {
  const page = await OrderDetailPage({
    params: Promise.resolve({ id: "1001" }),
    searchParams: Promise.resolve(justConfirmed ? { justConfirmed } : {}),
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
        productClosingAt: "2026-08-31T21:00:00+09:00",
      },
      { productId: 30, name: "대파 1단", quantity: 1, unitPrice: 2100, lineAmount: 2100, productClosingAt: "2026-08-31T21:00:00+09:00" },
    ],
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

function mockDetailError(code: string, status: number) {
  server.use(
    http.get(`${BASE}/api/orders/1001`, () =>
      HttpResponse.json(
        { code, message: "오류", serverTime: "2026-08-31T10:00:00+09:00" },
        { status }
      )
    )
  );
}

describe("SC-008 주문 완료 (justConfirmed=1 · CONFIRMED)", () => {
  it("기본 상태: 픽업 번호를 크게 보여주고 매장 정보와 다음 행동을 제공한다", async () => {
    mockAuthenticatedSession();
    mockDetail(sampleDetail());

    await renderPage("1");

    expect(await screen.findByText("주문이 확정됐어요")).toBeInTheDocument();
    const pickupNumber = screen.getByText("017");
    expect(pickupNumber).toHaveClass("font-display-pickup");
    expect(screen.getByText("매장에서 이 번호를 말해주세요")).toBeInTheDocument();
    expect(screen.getByText("2026-08-31 19:30~20:00")).toBeInTheDocument();
    expect(screen.getByText("14,100원")).toBeInTheDocument();
    expect(screen.getByText("ORD-20260831-000001")).toBeInTheDocument();
    expect(screen.getByText("savePick 성수점")).toBeInTheDocument();
    expect(screen.getByText("18:30까지 직접 취소할 수 있어요")).toBeInTheDocument();
    expect(screen.getByText("20:30까지 오지 않으면 노쇼로 처리돼요")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "픽업 안내 보기" })).toHaveAttribute("href", "/store");
  });

  it("주문 상세 보기·계속 둘러보기 버튼으로 이동한다", async () => {
    mockAuthenticatedSession();
    mockDetail(sampleDetail());

    await renderPage("1");
    await screen.findByText("주문이 확정됐어요");

    await userEvent.click(screen.getByRole("button", { name: "주문 상세 보기" }));
    expect(pushMock).toHaveBeenCalledWith("/orders/1001");

    await userEvent.click(screen.getByRole("button", { name: "계속 둘러보기" }));
    expect(pushMock).toHaveBeenCalledWith("/");
  });

  it("오류: 주문 내역으로 보내는 안내를 보여준다", async () => {
    mockAuthenticatedSession();
    mockDetailError("INTERNAL_ERROR", 500);

    await renderPage("1");

    expect(
      await screen.findByText("주문 정보를 불러오지 못했어요. 주문 내역에서 확인해주세요")
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "주문 내역으로" }));
    expect(pushMock).toHaveBeenCalledWith("/orders");
  });

  it("justConfirmed=1이어도 상태가 CONFIRMED가 아니면 주문 상세로 그린다", async () => {
    mockAuthenticatedSession();
    mockDetail(sampleDetail({ status: "COMPLETED", cancelable: false }));

    await renderPage("1");

    expect(await screen.findByText("주문 상품 2건")).toBeInTheDocument();
    expect(screen.queryByText("주문이 확정됐어요")).not.toBeInTheDocument();
  });
});

describe("SC-010 주문 상세", () => {
  it("기본 상태: 상태 배지·품목별 확정 단가·상태 변경 이력을 보여준다", async () => {
    mockAuthenticatedSession();
    mockDetail(sampleDetail());

    await renderPage();

    expect(await screen.findByText("주문 상품 2건")).toBeInTheDocument();
    // "확정"은 상태 배지와 상태 변경 이력 라벨 양쪽에 나온다. 앞쪽(배지)만 확인한다.
    expect(screen.getAllByText("확정")[0]).toHaveClass("bg-brand-weak", "text-brand");
    expect(screen.getByText("국내산 삼겹살 300g ×2")).toBeInTheDocument();
    expect(screen.getByText("확정 단가 6,000원")).toBeInTheDocument();
    expect(screen.getByText("12,000원")).toBeInTheDocument();
    expect(screen.getByText("상태 변경 이력")).toBeInTheDocument();
    expect(screen.getByText("2026-08-31 10:01")).toBeInTheDocument();
  });

  it("취소 가능하면 취소 화면으로 보내는 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    mockDetail(sampleDetail());

    await renderPage();
    await screen.findByText("주문 상품 2건");

    await userEvent.click(screen.getByRole("button", { name: "주문 취소하기" }));
    expect(pushMock).toHaveBeenCalledWith("/orders/1001/cancel");
  });

  it("취소 마감이 지났으면 버튼을 비활성화하고 이유를 캡션으로 보여준다", async () => {
    mockAuthenticatedSession();
    mockDetail(
      sampleDetail({ cancelable: false, cancelUnavailableReason: "CANCEL_DEADLINE_PASSED" })
    );

    await renderPage();

    expect(await screen.findByRole("button", { name: "주문 취소하기" })).toBeDisabled();
    expect(
      screen.getByText("18:30에 취소 가능 시각이 지났어요. 매장으로 문의해주세요")
    ).toBeInTheDocument();
  });

  it("READY: 준비 완료 안내를 보여준다", async () => {
    mockAuthenticatedSession();
    mockDetail(sampleDetail({ status: "READY" }));

    await renderPage();

    expect(await screen.findByText("매장에서 준비를 마쳤어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "주문 취소하기" })).toBeInTheDocument();
  });

  it("COMPLETED: 픽업 완료 시각을 보여주고 취소 버튼을 감춘다", async () => {
    mockAuthenticatedSession();
    mockDetail(
      sampleDetail({
        status: "COMPLETED",
        cancelable: false,
        cancelUnavailableReason: "ALREADY_COMPLETED",
        statusHistory: [
          {
            toStatus: "CONFIRMED",
            actorType: "CUSTOMER",
            occurredAt: "2026-08-31T10:01:00+09:00",
          },
          { toStatus: "COMPLETED", actorType: "ADMIN", occurredAt: "2026-08-31T19:42:00+09:00" },
        ],
      })
    );

    await renderPage();

    expect(await screen.findByText("19:42에 픽업이 완료된 주문이에요")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "주문 취소하기" })).not.toBeInTheDocument();
  });

  it("관리자 취소: 취소 주체와 사유를 보여준다", async () => {
    mockAuthenticatedSession();
    mockDetail(
      sampleDetail({
        status: "CANCELED",
        cancelable: false,
        cancelUnavailableReason: "ALREADY_CANCELED",
        canceledBy: "ADMIN",
        cancelReason: "재고 파손으로 준비가 어려워요",
        statusHistory: [
          {
            toStatus: "CONFIRMED",
            actorType: "CUSTOMER",
            occurredAt: "2026-08-31T10:01:00+09:00",
          },
          { toStatus: "CANCELED", actorType: "ADMIN", occurredAt: "2026-08-31T11:20:00+09:00" },
        ],
      })
    );

    await renderPage();

    expect(await screen.findByText("2026-08-31 11:20에 취소된 주문이에요")).toBeInTheDocument();
    expect(screen.getByText("관리자가 취소했어요")).toBeInTheDocument();
    expect(screen.getByText("재고 파손으로 준비가 어려워요")).toBeInTheDocument();
  });

  it("NO_SHOW: 환불 불가 안내와 최근 30일 노쇼 횟수를 보여준다", async () => {
    mockAuthenticatedSession();
    mockDetail(
      sampleDetail({
        status: "NO_SHOW",
        cancelable: false,
        cancelUnavailableReason: "NO_SHOW",
        noShowAt: "2026-08-31T20:30:00+09:00",
        refunded: false,
      })
    );
    server.use(
      http.get(`${BASE}/api/me/no-show-status`, () =>
        HttpResponse.json({ recentNoShowCount: 2, orderBlocked: false, blockedUntil: null })
      )
    );

    await renderPage();

    expect(
      await screen.findByText("20:30에 노쇼로 처리됐어요. 결제 금액은 환불되지 않아요")
    ).toBeInTheDocument();
    expect(await screen.findByText("최근 30일 노쇼 2회")).toBeInTheDocument();
  });

  it("FAILED: 픽업 번호가 없고 결제 실패 안내를 보여준다", async () => {
    mockAuthenticatedSession();
    mockDetail(
      sampleDetail({
        status: "FAILED",
        pickupNumber: null,
        cancelable: false,
        cancelableUntil: null,
        noShowDueAt: null,
        statusHistory: [],
      })
    );

    await renderPage();

    expect(
      await screen.findByText("결제가 3회 실패해 종료된 주문이에요. 픽업 번호는 발급되지 않았어요")
    ).toBeInTheDocument();
    expect(screen.getByText("픽업 번호가 없는 주문이에요")).toBeInTheDocument();
  });

  it("오류(없는 주문): 조회할 수 없는 주문 안내를 보여준다", async () => {
    mockAuthenticatedSession();
    mockDetailError("NOT_FOUND", 404);

    await renderPage();

    expect(await screen.findByText("조회할 수 없는 주문이에요")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "주문 내역으로" }));
    expect(pushMock).toHaveBeenCalledWith("/orders");
  });

  it("오류(통신): 다시 시도하면 다시 불러온다", async () => {
    mockAuthenticatedSession();
    let attempt = 0;
    server.use(
      http.get(`${BASE}/api/orders/1001`, () => {
        attempt += 1;
        if (attempt === 1) {
          return HttpResponse.json(
            { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T10:00:00+09:00" },
            { status: 500 }
          );
        }
        return HttpResponse.json(sampleDetail());
      })
    );

    await renderPage();

    await screen.findByText("주문 정보를 불러오지 못했어요");
    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(await screen.findByText("주문 상품 2건")).toBeInTheDocument();
  });

  it("비로그인이면 로그인 화면으로 보낸다", async () => {
    mockGuestSession();

    await renderPage();

    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/login"));
  });
});
