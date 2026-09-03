import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AuthProvider } from "@/lib/auth/customer-auth";
import { setServerTimeOffsetMs } from "@/lib/server-time";
import OrderDraftPage from "./page";

const BASE = "http://test.local";
const pushMock = vi.fn();
const replaceMock = vi.fn();
let searchParamsValue = "orderId=1001";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock, replace: replaceMock }),
  useSearchParams: () => new URLSearchParams(searchParamsValue),
}));

function renderPage() {
  return render(
    <AuthProvider>
      <OrderDraftPage />
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
        { code: "UNAUTHENTICATED", message: "인증이 필요해요.", serverTime: "2026-08-31T00:00:00+09:00" },
        { status: 401 }
      )
    )
  );
}

function installOrderDetail(overrides: Record<string, unknown> = {}) {
  server.use(
    http.get(`${BASE}/api/orders/1001`, () =>
      HttpResponse.json({
        serverTime: "2026-08-31T10:00:00+09:00",
        orderId: 1001,
        orderNo: "ORD-20260831-000001",
        status: "PENDING",
        orderedAt: "2026-08-31T10:00:00+09:00",
        items: [
          { productId: 12, name: "국내산 삼겹살 300g", quantity: 2, unitPrice: 6000, lineAmount: 12000 },
          { productId: 30, name: "대파 1단", quantity: 1, unitPrice: 2100, lineAmount: 2100 },
        ],
        totalAmount: 14100,
        pickupNumber: null,
        pickupStartAt: null,
        pickupEndAt: null,
        noShowDueAt: null,
        cancelable: false,
        cancelableUntil: null,
        cancelUnavailableReason: null,
        canceledBy: null,
        cancelReason: null,
        ...overrides,
      })
    )
  );
}

function installHold(overrides: Record<string, unknown> = {}) {
  server.use(
    http.get(`${BASE}/api/orders/1001/hold`, () =>
      HttpResponse.json({
        orderId: 1001,
        status: "PENDING",
        serverTime: "2026-08-31T10:00:00+09:00",
        holdExpiresAt: "2026-08-31T10:09:58+09:00",
        holdRemainingSeconds: 598,
        expiringSoon: false,
        paymentAttemptRemaining: 3,
        ...overrides,
      })
    )
  );
}

function installMe() {
  server.use(
    http.get(`${BASE}/api/me`, () =>
      HttpResponse.json({
        memberId: 1,
        email: "minsu@example.com",
        name: "김지현",
        phone: "01000000000",
        orderPermission: "ALLOWED",
      })
    )
  );
}

describe("SC-005 주문서 작성", () => {
  beforeEach(() => {
    searchParamsValue = "orderId=1001";
    setServerTimeOffsetMs(0);
    // 고정 시각(테스트 픽스처가 쓰는 "2026-08-31 10:00 KST" 기준) + 실제 경과 시간만큼
    // 자동으로 흘러가는 가짜 타이머를 쓴다 — HoldTimerBar가 테스트 실행 시점의 실제
    // 벽시계 시각과 픽스처의 만료 시각을 비교해 우연히 "이미 만료됨"으로 판정하는
    // 것을 막으면서도, RTL의 waitFor/findBy는 그대로 동작하게 한다.
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date("2026-08-31T01:00:00.000Z")); // KST 10:00:00
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("기본 상태: 타이머·품목·확정 금액·픽업 연락처와 다음 단계 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    installOrderDetail();
    installHold();
    installMe();
    renderPage();

    await screen.findByText("국내산 삼겹살 300g ×2");
    expect(screen.getByText("대파 1단 ×1")).toBeInTheDocument();
    expect(screen.getAllByText("14,100원").length).toBeGreaterThan(0);
    expect(screen.getByText("김지현 · 01000000000")).toBeInTheDocument();
    expect(screen.getByText("주문서 금액은 지금 시점의 할인가로 고정돼요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "픽업 시간 고르기" })).toBeEnabled();
    expect(screen.getByRole("status")).toHaveTextContent("선점 시간 09:58 남음");
  });

  it("로딩 상태: 재고를 확보하는 중이에요를 보여주고 이탈 버튼이 없다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/orders/1001`, async () => {
        await new Promise((resolve) => setTimeout(resolve, 30));
        return HttpResponse.json(
          { code: "NOT_FOUND", message: "없음", serverTime: "2026-08-31T10:00:00+09:00" },
          { status: 404 }
        );
      })
    );
    installHold();
    installMe();
    renderPage();

    expect(screen.getByText("재고를 확보하는 중이에요")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "주문서 포기" })).not.toBeInTheDocument();
  });

  it("만료 임박: 잔여 60초 이하면 타이머 문구와 만료 임박 배너를 함께 보여준다", async () => {
    mockAuthenticatedSession();
    installOrderDetail();
    installHold({
      holdExpiresAt: "2026-08-31T10:00:45+09:00",
      holdRemainingSeconds: 45,
      expiringSoon: true,
    });
    installMe();
    renderPage();

    await screen.findByText("국내산 삼겹살 300g ×2");
    await screen.findByText("1분 뒤 선점이 풀려요. 시간은 연장되지 않아요");
    expect(screen.getByRole("status")).toHaveTextContent("00:45 남음 · 시간은 연장되지 않아요");
  });

  it("선점 만료: 화면을 덮는 시트를 보여주고 장바구니로 이동한다", async () => {
    mockAuthenticatedSession();
    installOrderDetail();
    installHold({ status: "EXPIRED", holdRemainingSeconds: 0 });
    installMe();
    renderPage();

    await screen.findByText("선점 시간이 끝났어요");
    expect(
      screen.getByText("선점한 수량은 다른 고객이 살 수 있게 돌아갔어요")
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "장바구니에서 다시 주문하기" }));
    expect(replaceMock).toHaveBeenCalledWith("/cart");
  });

  // TC-110·TC-121(X5): lock_timeout 초과가 INTERNAL_ERROR(500)로 올라올 때 SC-005가
  // 오류 상태로 노출하는지 확인한다.
  it("TC-110 오류(통신): 주문서를 불러오지 못했어요와 다시 시도를 보여주고 재시도하면 복구된다", async () => {
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
        return HttpResponse.json({
          serverTime: "2026-08-31T10:00:00+09:00",
          orderId: 1001,
          orderNo: "ORD-20260831-000001",
          status: "PENDING",
          orderedAt: "2026-08-31T10:00:00+09:00",
          items: [
            { productId: 12, name: "국내산 삼겹살 300g", quantity: 2, unitPrice: 6000, lineAmount: 12000 },
          ],
          totalAmount: 12000,
          pickupNumber: null,
          pickupStartAt: null,
          pickupEndAt: null,
          noShowDueAt: null,
          cancelable: false,
          cancelableUntil: null,
          cancelUnavailableReason: null,
          canceledBy: null,
          cancelReason: null,
        });
      })
    );
    installHold();
    installMe();
    renderPage();

    await screen.findByText("주문서를 불러오지 못했어요");
    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    await screen.findByText("국내산 삼겹살 300g ×2");
  });

  it("주문서 포기: 포기 요청 후 장바구니로 이동한다", async () => {
    mockAuthenticatedSession();
    installOrderDetail();
    installHold();
    installMe();
    let abandonCalled = false;
    server.use(
      http.delete(`${BASE}/api/orders/1001`, () => {
        abandonCalled = true;
        return HttpResponse.json({
          orderId: 1001,
          status: "EXPIRED",
          releasedAt: "2026-08-31T10:00:00+09:00",
        });
      })
    );
    renderPage();

    await screen.findByText("국내산 삼겹살 300g ×2");
    await userEvent.click(screen.getByRole("button", { name: "주문서 포기" }));

    await waitFor(() => expect(abandonCalled).toBe(true));
    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/cart"));
  });

  it("픽업 시간 고르기: 다음 단계로 이동한다", async () => {
    mockAuthenticatedSession();
    installOrderDetail();
    installHold();
    installMe();
    renderPage();

    await screen.findByText("국내산 삼겹살 300g ×2");
    await userEvent.click(screen.getByRole("button", { name: "픽업 시간 고르기" }));

    expect(pushMock).toHaveBeenCalledWith("/orders/new/pickup?orderId=1001");
  });

  it("비로그인 상태면 로그인 화면으로 보낸다", async () => {
    mockGuestSession();
    renderPage();

    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/login"));
  });
});
