import { describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AdminAuthProvider } from "@/lib/auth/admin-auth";
import PickupLookupPage from "./page";

const BASE = "http://test.local";

function renderPage() {
  return render(
    <AdminAuthProvider>
      <PickupLookupPage />
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
      { productId: 12, name: "삼겹살 500g", quantity: 2, unitPrice: 8400, lineAmount: 16800 },
      { productId: 13, name: "대파 1단", quantity: 1, unitPrice: 2100, lineAmount: 2100 },
    ],
    totalAmount: 18900,
    paymentAttempts: [],
    statusHistory: [
      { fromStatus: null, toStatus: "PENDING", actorType: "CUSTOMER", occurredAt: "2026-08-28T18:23:00+09:00" },
      { fromStatus: "PENDING", toStatus: "CONFIRMED", actorType: "CUSTOMER", occurredAt: "2026-08-28T18:24:00+09:00" },
    ],
    availableActions: ["READY", "COMPLETE", "CANCEL"],
    ...overrides,
  };
}

async function enterAndSubmit(value: string) {
  const input = screen.getByLabelText("픽업 번호 3자리");
  await userEvent.type(input, value);
  await userEvent.click(screen.getByRole("button", { name: "조회" }));
}

describe("SC-109 픽업 번호 조회", () => {
  it("기본(조회 전): 오늘 영업일 기준 안내와 조회 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    renderPage();

    expect(
      screen.getByText("오늘 영업일 기준으로 찾아요. 다른 영업일의 같은 번호는 함께 조회하지 않아요.")
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "조회" })).toBeDisabled();
  });

  it("로딩 상태: 결과 자리 스켈레톤을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/orders/by-pickup-number`, async () => {
        await new Promise((resolve) => setTimeout(resolve, 30));
        return HttpResponse.json(sampleDetail());
      })
    );
    renderPage();

    await enterAndSubmit("42");

    expect(screen.getAllByRole("status", { name: "불러오는 중" }).length).toBeGreaterThan(0);
    expect(await screen.findByText("주문을 찾는 중이에요")).toBeInTheDocument();
  });

  it("조회 결과: 픽업 번호·고객 이름·픽업 시간대·품목 수·결제 금액을 보여준다", async () => {
    mockAuthenticatedSession();
    let requestedUrl: URL | null = null;
    server.use(
      http.get(`${BASE}/api/admin/orders/by-pickup-number`, ({ request }) => {
        requestedUrl = new URL(request.url);
        return HttpResponse.json(sampleDetail());
      })
    );
    renderPage();

    await enterAndSubmit("42");

    expect(await screen.findByText("픽업 번호 042")).toBeInTheDocument();
    expect(screen.getByText("김지현")).toBeInTheDocument();
    expect(screen.getByText("2건 (3개)")).toBeInTheDocument();
    expect(screen.getByText("18,900원")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "픽업 완료 처리" })).not.toBeDisabled();

    // 3자리 미만 입력도 0을 채워 조회한다(docs/09 §5 규칙 7).
    expect((requestedUrl as unknown as URL | null)?.searchParams.get("pickupNumber")).toBe("042");
  });

  it("빈 상태: 픽업 번호 042에 해당하는 주문이 없어요 문구를 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/orders/by-pickup-number`, () =>
        HttpResponse.json(
          { code: "NOT_FOUND", message: "찾을 수 없어요.", serverTime: "2026-08-28T18:00:00+09:00" },
          { status: 404 }
        )
      )
    );
    renderPage();

    await enterAndSubmit("42");

    expect(await screen.findByText("픽업 번호 042에 해당하는 주문이 없어요")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "주문 목록으로" })).toHaveAttribute(
      "href",
      "/admin/orders"
    );
  });

  it("빈 상태에서 번호를 고쳐 바로 다시 조회할 수 있다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/orders/by-pickup-number`, ({ request }) =>
        new URL(request.url).searchParams.get("pickupNumber") === "042"
          ? HttpResponse.json(
              {
                code: "NOT_FOUND",
                message: "찾을 수 없어요.",
                serverTime: "2026-08-28T18:00:00+09:00",
              },
              { status: 404 }
            )
          : HttpResponse.json(sampleDetail())
      )
    );
    renderPage();

    await enterAndSubmit("42");
    await screen.findByText("픽업 번호 042에 해당하는 주문이 없어요");

    await userEvent.clear(screen.getByLabelText("픽업 번호 3자리"));
    await enterAndSubmit("117");

    expect(await screen.findByText("김지현")).toBeInTheDocument();
  });

  it("오류(이미 완료): 완료 시각과 함께 픽업 완료 처리 버튼을 비활성화한다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/orders/by-pickup-number`, () =>
        HttpResponse.json(
          sampleDetail({
            status: "COMPLETED",
            availableActions: [],
            statusHistory: [
              ...sampleDetail().statusHistory,
              { fromStatus: "CONFIRMED", toStatus: "COMPLETED", actorType: "ADMIN", occurredAt: "2026-08-28T20:12:00+09:00" },
            ],
          })
        )
      )
    );
    renderPage();

    await enterAndSubmit("42");

    expect(await screen.findByText("이미 20:12에 픽업 완료된 주문이에요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "픽업 완료 처리" })).toBeDisabled();
  });

  it("오류(노쇼): 노쇼로 처리돼 수령할 수 없어요 문구를 보여주고 버튼을 비활성화한다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/orders/by-pickup-number`, () =>
        HttpResponse.json(sampleDetail({ status: "NO_SHOW", availableActions: [] }))
      )
    );
    renderPage();

    await enterAndSubmit("42");

    expect(await screen.findByText("노쇼로 처리돼 수령할 수 없어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "픽업 완료 처리" })).toBeDisabled();
  });

  it("오류(통신): 불러오지 못했어요 문구와 다시 시도 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/orders/by-pickup-number`, () =>
        HttpResponse.json(
          { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-28T18:00:00+09:00" },
          { status: 500 }
        )
      )
    );
    renderPage();

    await enterAndSubmit("42");

    expect(await screen.findByText("불러오지 못했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("픽업 완료 처리 버튼을 누르면 완료 처리하고 결과 카드를 새로고침한다", async () => {
    mockAuthenticatedSession();
    let completeCalled = false;
    server.use(
      http.get(`${BASE}/api/admin/orders/by-pickup-number`, () =>
        HttpResponse.json(sampleDetail())
      ),
      http.post(`${BASE}/api/admin/orders/1001/complete`, () => {
        completeCalled = true;
        return HttpResponse.json({
          orderId: 1001,
          status: "COMPLETED",
          completedAt: "2026-08-28T20:12:00+09:00",
          stockChanged: false,
        });
      })
    );
    renderPage();
    await enterAndSubmit("42");
    await screen.findByText("픽업 번호 042");

    server.use(
      http.get(`${BASE}/api/admin/orders/by-pickup-number`, () =>
        HttpResponse.json(
          sampleDetail({
            status: "COMPLETED",
            availableActions: [],
            statusHistory: [
              ...sampleDetail().statusHistory,
              { fromStatus: "CONFIRMED", toStatus: "COMPLETED", actorType: "ADMIN", occurredAt: "2026-08-28T20:12:00+09:00" },
            ],
          })
        )
      )
    );

    await userEvent.click(screen.getByRole("button", { name: "픽업 완료 처리" }));

    expect(await screen.findByText("이미 20:12에 픽업 완료된 주문이에요")).toBeInTheDocument();
    expect(completeCalled).toBe(true);
  });

  it("픽업 완료 처리 실패(이미 완료): 안내 시트를 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/orders/by-pickup-number`, () =>
        HttpResponse.json(sampleDetail())
      ),
      http.post(`${BASE}/api/admin/orders/1001/complete`, () =>
        HttpResponse.json(
          {
            code: "INVALID_ORDER_STATUS",
            message: "이미 완료된 주문이에요",
            serverTime: "2026-08-28T18:00:00+09:00",
            details: { currentStatus: "COMPLETED" },
          },
          { status: 409 }
        )
      )
    );
    renderPage();
    await enterAndSubmit("42");
    await screen.findByText("픽업 번호 042");

    await userEvent.click(screen.getByRole("button", { name: "픽업 완료 처리" }));

    await waitFor(() =>
      expect(screen.getByRole("dialog").textContent).toContain("이미 완료 처리된 주문이에요")
    );
  });
});
