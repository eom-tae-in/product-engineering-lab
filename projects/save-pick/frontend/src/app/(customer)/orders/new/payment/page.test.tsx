import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AuthProvider } from "@/lib/auth/customer-auth";
import { setServerTimeOffsetMs } from "@/lib/server-time";
import PaymentPage from "./page";

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
      <PaymentPage />
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
        pickupStartAt: "2026-08-31T19:30:00+09:00",
        pickupEndAt: "2026-08-31T20:00:00+09:00",
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

async function renderReady() {
  mockAuthenticatedSession();
  installOrderDetail();
  installHold();
  renderPage();
  await screen.findByRole("button", { name: /결제하기$/ });
}

describe("SC-007 결제 확인 및 결과", () => {
  beforeEach(() => {
    searchParamsValue = "orderId=1001";
    setServerTimeOffsetMs(0);
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date("2026-08-31T01:00:00.000Z")); // KST 10:00:00
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("기본 상태: 픽업 시간대·품목 요약·확정 금액과 결제 버튼을 보여준다", async () => {
    await renderReady();

    expect(screen.getByText("픽업 19:30~20:00")).toBeInTheDocument();
    expect(screen.getByText("국내산 삼겹살 300g ×2")).toBeInTheDocument();
    expect(screen.getAllByText("14,100원").length).toBeGreaterThan(0);
    expect(
      screen.getByText("가상 결제입니다. 실제 금전 거래는 일어나지 않아요")
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "14,100원 결제하기" })).toBeEnabled();
  });

  it("로딩(버튼): 결제 처리 중에는 문구가 바뀌고 중복 탭을 막는다", async () => {
    await renderReady();
    let resolvePayment!: (response: Response) => void;
    server.use(
      http.post(`${BASE}/api/orders/1001/payments`, () => {
        return new Promise<Response>((resolve) => {
          resolvePayment = resolve;
        });
      })
    );

    const button = screen.getByRole("button", { name: "14,100원 결제하기" });
    await userEvent.click(button);

    expect(screen.getByRole("button", { name: "결제 결과를 확인하는 중이에요" })).toBeDisabled();

    resolvePayment(
      HttpResponse.json({
        result: "SUCCEEDED",
        orderId: 1001,
        orderNo: "ORD-20260831-000001",
        status: "CONFIRMED",
        pickupNumber: "017",
        pickupBusinessDate: "2026-08-31",
        pickupStartAt: "2026-08-31T19:30:00+09:00",
        pickupEndAt: "2026-08-31T20:00:00+09:00",
        paidAmount: 14100,
        cancelableUntil: "2026-08-31T18:30:00+09:00",
        noShowDueAt: "2026-08-31T20:30:00+09:00",
        confirmedAt: "2026-08-31T10:01:00+09:00",
      })
    );
    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/orders/1001?justConfirmed=1"));
  });

  it("결제 실패(1회): 재시도 횟수와 남은 선점 시간을 보여준다", async () => {
    await renderReady();
    server.use(
      http.post(`${BASE}/api/orders/1001/payments`, () =>
        HttpResponse.json({
          result: "FAILED",
          code: "PAYMENT_FAILED",
          orderId: 1001,
          status: "PENDING",
          attemptNo: 1,
          paymentAttemptRemaining: 2,
          holdExpiresAt: "2026-08-31T10:08:12+09:00",
          holdRemainingSeconds: 492,
          failureReason: "DECLINED",
          message: "결제가 실패했습니다.",
        })
      )
    );

    await userEvent.click(screen.getByRole("button", { name: "14,100원 결제하기" }));

    await screen.findByText("결제가 완료되지 않았어요");
    expect(
      screen.getByText("선점은 08:12 남았고, 2번 더 시도할 수 있어요")
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 결제하기" })).toBeInTheDocument();
  });

  it("결제 실패(2회): 남은 횟수를 경고색으로 보여준다", async () => {
    await renderReady();
    server.use(
      http.post(`${BASE}/api/orders/1001/payments`, () =>
        HttpResponse.json({
          result: "FAILED",
          code: "PAYMENT_FAILED",
          orderId: 1001,
          status: "PENDING",
          attemptNo: 2,
          paymentAttemptRemaining: 1,
          holdExpiresAt: "2026-08-31T10:07:00+09:00",
          holdRemainingSeconds: 420,
          failureReason: "DECLINED",
          message: "결제가 실패했습니다.",
        })
      )
    );

    await userEvent.click(screen.getByRole("button", { name: "14,100원 결제하기" }));

    const message = await screen.findByText("선점은 07:00 남았고, 1번 더 시도할 수 있어요");
    expect(message).toHaveClass("text-warning");
  });

  it("결제 실패(3회·최종): 전체 화면으로 종료 안내를 보여준다", async () => {
    await renderReady();
    server.use(
      http.post(`${BASE}/api/orders/1001/payments`, () =>
        HttpResponse.json({
          result: "FAILED",
          code: "PAYMENT_FAILED",
          orderId: 1001,
          status: "FAILED",
          attemptNo: 3,
          paymentAttemptRemaining: 0,
          holdReleased: true,
          failureReason: "TIMEOUT",
          message: "결제가 3회 실패해 주문이 종료됐습니다.",
        })
      )
    );

    await userEvent.click(screen.getByRole("button", { name: "14,100원 결제하기" }));

    await screen.findByText("주문이 종료됐어요");
    expect(screen.getByText("선점한 수량은 다시 판매 가능해졌어요")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "14,100원 결제하기" })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "장바구니에서 다시 주문하기" }));
    expect(pushMock).toHaveBeenCalledWith("/cart");
  });

  it("선점 만료: 화면을 덮는 시트를 보여준다", async () => {
    mockAuthenticatedSession();
    installOrderDetail();
    installHold({ status: "EXPIRED", holdRemainingSeconds: 0 });
    renderPage();

    await screen.findByText("선점 시간이 끝났어요");
  });

  it("오류(금액 불일치): 주문서로 이동 버튼을 보여준다", async () => {
    await renderReady();
    server.use(
      http.post(`${BASE}/api/orders/1001/payments`, () =>
        HttpResponse.json(
          {
            code: "AMOUNT_MISMATCH",
            message: "금액이 달라요.",
            serverTime: "2026-08-31T10:00:00+09:00",
            details: { expectedAmount: 14100 },
          },
          { status: 409 }
        )
      )
    );

    await userEvent.click(screen.getByRole("button", { name: "14,100원 결제하기" }));

    await screen.findByText("주문 금액이 달라졌어요. 주문서를 다시 확인해주세요");
    await userEvent.click(screen.getByRole("button", { name: "주문서로 이동" }));
    expect(pushMock).toHaveBeenCalledWith("/orders/new?orderId=1001");
  });

  // TC-125(X1 보강분): API-022 4단계 재검사에서 SLOT_CLOSED가 나면 SC-007이 전용 시트로
  // SC-006 재선택을 유도해야 한다.
  it("TC-125 오류(결제 직전 예약 마감): 다른 시간대 고르기로 보낸다", async () => {
    await renderReady();
    server.use(
      http.post(`${BASE}/api/orders/1001/payments`, () =>
        HttpResponse.json(
          { code: "SLOT_CLOSED", message: "마감됐어요.", serverTime: "2026-08-31T10:00:00+09:00" },
          { status: 409 }
        )
      )
    );

    await userEvent.click(screen.getByRole("button", { name: "14,100원 결제하기" }));

    await screen.findByText("선택한 시간대의 예약이 방금 마감됐어요");
    await userEvent.click(screen.getByRole("button", { name: "다른 시간대 고르기" }));
    expect(pushMock).toHaveBeenCalledWith("/orders/new/pickup?orderId=1001");
  });

  it("오류(시간대 정원 소진): 다른 시간대 고르기로 보낸다", async () => {
    await renderReady();
    server.use(
      http.post(`${BASE}/api/orders/1001/payments`, () =>
        HttpResponse.json(
          { code: "SLOT_FULL", message: "정원이 찼어요.", serverTime: "2026-08-31T10:00:00+09:00" },
          { status: 409 }
        )
      )
    );

    await userEvent.click(screen.getByRole("button", { name: "14,100원 결제하기" }));

    await screen.findByText("선택한 시간대의 정원이 찼어요");
    expect(screen.getByRole("button", { name: "다른 시간대 고르기" })).toBeInTheDocument();
  });

  it("오류(통신·무응답): 실패로 처리했다는 안내를 함께 보여준다", async () => {
    await renderReady();
    server.use(
      http.post(`${BASE}/api/orders/1001/payments`, () =>
        HttpResponse.json({
          result: "FAILED",
          code: "PAYMENT_FAILED",
          orderId: 1001,
          status: "PENDING",
          attemptNo: 1,
          paymentAttemptRemaining: 2,
          holdExpiresAt: "2026-08-31T10:08:12+09:00",
          holdRemainingSeconds: 492,
          failureReason: "TIMEOUT",
          message: "응답 없음",
        })
      )
    );

    await userEvent.click(screen.getByRole("button", { name: "14,100원 결제하기" }));

    await screen.findByText("결제가 완료되지 않았어요");
    expect(screen.getByText("응답을 받지 못해 실패로 처리했어요")).toBeInTheDocument();
  });

  it("오류(시스템 오류): 다시 시도를 보여주고 재시도하면 성공한다", async () => {
    await renderReady();
    let attempt = 0;
    server.use(
      http.post(`${BASE}/api/orders/1001/payments`, () => {
        attempt += 1;
        if (attempt === 1) {
          return HttpResponse.json(
            { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T10:00:00+09:00" },
            { status: 500 }
          );
        }
        return HttpResponse.json({
          result: "SUCCEEDED",
          orderId: 1001,
          orderNo: "ORD-20260831-000001",
          status: "CONFIRMED",
          pickupNumber: "017",
          pickupBusinessDate: "2026-08-31",
          pickupStartAt: "2026-08-31T19:30:00+09:00",
          pickupEndAt: "2026-08-31T20:00:00+09:00",
          paidAmount: 14100,
          cancelableUntil: "2026-08-31T18:30:00+09:00",
          noShowDueAt: "2026-08-31T20:30:00+09:00",
          confirmedAt: "2026-08-31T10:01:00+09:00",
        });
      })
    );

    await userEvent.click(screen.getByRole("button", { name: "14,100원 결제하기" }));

    await screen.findByText(
      "일시적인 오류로 결제를 처리하지 못했어요. 잠시 뒤 다시 시도해주세요"
    );
    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/orders/1001?justConfirmed=1"));
  });

  it("결제 시도마다 새 Idempotency-Key를 보낸다", async () => {
    await renderReady();
    const keys: (string | null)[] = [];
    server.use(
      http.post(`${BASE}/api/orders/1001/payments`, ({ request }) => {
        keys.push(request.headers.get("Idempotency-Key"));
        return HttpResponse.json({
          result: "FAILED",
          code: "PAYMENT_FAILED",
          orderId: 1001,
          status: "PENDING",
          attemptNo: keys.length,
          paymentAttemptRemaining: 3 - keys.length,
          holdExpiresAt: "2026-08-31T10:08:00+09:00",
          holdRemainingSeconds: 480,
          failureReason: "DECLINED",
          message: "실패",
        });
      })
    );

    await userEvent.click(screen.getByRole("button", { name: "14,100원 결제하기" }));
    await screen.findByText("결제가 완료되지 않았어요");
    await userEvent.click(screen.getByRole("button", { name: "다시 결제하기" }));
    await waitFor(() => expect(keys.length).toBe(2));

    expect(keys[0]).toBeTruthy();
    expect(keys[1]).toBeTruthy();
    expect(keys[0]).not.toEqual(keys[1]);
  });

  it("시간대 다시 고르기·주문서 포기 버튼을 제공한다", async () => {
    await renderReady();

    await userEvent.click(screen.getByRole("button", { name: "시간대 다시 고르기" }));
    expect(pushMock).toHaveBeenCalledWith("/orders/new/pickup?orderId=1001");
  });
});
