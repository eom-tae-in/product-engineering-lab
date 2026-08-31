import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AuthProvider } from "@/lib/auth/customer-auth";
import { setServerTimeOffsetMs } from "@/lib/server-time";
import type { PickupSlotsResponse } from "@/features/order/types";
import PickupSlotSelectionPage from "./page";

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
      <PickupSlotSelectionPage />
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

function installHold(overrides: Record<string, unknown> = {}) {
  server.use(
    http.get(`${BASE}/api/orders/1001/hold`, () =>
      HttpResponse.json({
        orderId: 1001,
        status: "PENDING",
        serverTime: "2026-08-31T10:00:00+09:00",
        holdExpiresAt: "2026-08-31T10:08:00+09:00",
        holdRemainingSeconds: 480,
        expiringSoon: false,
        paymentAttemptRemaining: 3,
        ...overrides,
      })
    )
  );
}

function baseSlots(): PickupSlotsResponse {
  return {
    serverTime: "2026-08-31T10:00:00+09:00",
    selectableDates: [
      { date: "2026-08-31", label: "D+0", selectable: true },
      { date: "2026-09-01", label: "D+1", selectable: true },
    ],
    slots: [
      {
        slotId: 341,
        date: "2026-08-31",
        startAt: "2026-08-31T19:30:00+09:00",
        endAt: "2026-08-31T20:00:00+09:00",
        capacity: 20,
        reservedCount: 18,
        selectable: true,
      },
      {
        slotId: 342,
        date: "2026-08-31",
        startAt: "2026-08-31T20:00:00+09:00",
        endAt: "2026-08-31T20:30:00+09:00",
        capacity: 20,
        reservedCount: 20,
        selectable: false,
        unselectableReason: "SLOT_FULL",
      },
      {
        slotId: 340,
        date: "2026-08-31",
        startAt: "2026-08-31T19:00:00+09:00",
        endAt: "2026-08-31T19:30:00+09:00",
        capacity: 20,
        reservedCount: 5,
        selectable: false,
        unselectableReason: "RESERVATION_CLOSED",
      },
    ],
  };
}

function installSlots(payload: PickupSlotsResponse) {
  server.use(http.get(`${BASE}/api/orders/1001/pickup-slots`, () => HttpResponse.json(payload)));
}

describe("SC-006 픽업 날짜·시간대 선택", () => {
  beforeEach(() => {
    searchParamsValue = "orderId=1001";
    setServerTimeOffsetMs(0);
    // 고정 시각(테스트 픽스처가 쓰는 "2026-08-31 10:00 KST" 기준) + 실제 경과 시간만큼
    // 자동으로 흘러가는 가짜 타이머를 쓴다 — HoldTimerBar가 실행 시점의 실제 벽시계
    // 시각과 픽스처의 만료 시각을 비교해 우연히 "이미 만료됨"으로 판정하는 것을
    // 막으면서도, RTL의 waitFor/findBy가 그대로 동작하게 한다.
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date("2026-08-31T01:00:00.000Z")); // KST 10:00:00
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("기본 상태: 날짜 탭과 시간대 칩 그리드를 보여준다", async () => {
    mockAuthenticatedSession();
    installHold();
    installSlots(baseSlots());
    renderPage();

    await screen.findByRole("button", { name: "19:30~20:00 18/20" });
    expect(screen.getByRole("button", { name: "20:00~20:30 정원 20/20" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "19:00~19:30 마감" })).toBeDisabled();
    expect(screen.getByRole("status")).toHaveTextContent("선점 시간 08:00 남음");
  });

  it("로딩 상태: 칩 스켈레톤 12개를 보여준다", async () => {
    mockAuthenticatedSession();
    installHold();
    server.use(
      http.get(`${BASE}/api/orders/1001/pickup-slots`, async () => {
        await new Promise((resolve) => setTimeout(resolve, 30));
        return HttpResponse.json(baseSlots());
      })
    );
    renderPage();

    expect(screen.getAllByRole("status", { name: "불러오는 중" }).length).toBeGreaterThanOrEqual(12);
    await screen.findByRole("button", { name: "19:30~20:00 18/20" });
  });

  it("빈 상태(오늘 전부 불가): 자동으로 내일 탭이 선택되고, 오늘 탭엔 안내 문구가 뜬다", async () => {
    mockAuthenticatedSession();
    installHold();
    installSlots({
      serverTime: "2026-08-31T10:00:00+09:00",
      selectableDates: [
        { date: "2026-08-31", label: "D+0", selectable: true },
        { date: "2026-09-01", label: "D+1", selectable: true },
      ],
      slots: [
        {
          slotId: 340,
          date: "2026-08-31",
          startAt: "2026-08-31T19:00:00+09:00",
          endAt: "2026-08-31T19:30:00+09:00",
          capacity: 20,
          reservedCount: 20,
          selectable: false,
          unselectableReason: "SLOT_FULL",
        },
        {
          slotId: 441,
          date: "2026-09-01",
          startAt: "2026-09-01T10:00:00+09:00",
          endAt: "2026-09-01T10:30:00+09:00",
          capacity: 20,
          reservedCount: 0,
          selectable: true,
        },
      ],
    });
    renderPage();

    await screen.findByRole("button", { name: "10:00~10:30 0/20" });

    await userEvent.click(screen.getByRole("button", { name: "오늘" }));
    expect(await screen.findByText("오늘은 선택할 수 있는 시간대가 없어요")).toBeInTheDocument();
  });

  it("빈 상태(양일 전부 불가): 사유와 함께 주문서 포기 버튼을 보여주고 장바구니로 이동한다", async () => {
    mockAuthenticatedSession();
    installHold();
    installSlots({
      serverTime: "2026-08-31T10:00:00+09:00",
      selectableDates: [
        { date: "2026-08-31", label: "D+0", selectable: true },
        { date: "2026-09-01", label: "D+1", selectable: false, unselectableReason: "AFTER_PRODUCT_CLOSING" },
      ],
      slots: [
        {
          slotId: 340,
          date: "2026-08-31",
          startAt: "2026-08-31T19:00:00+09:00",
          endAt: "2026-08-31T19:30:00+09:00",
          capacity: 20,
          reservedCount: 20,
          selectable: false,
          unselectableReason: "SLOT_FULL",
        },
      ],
    });
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

    await screen.findByText("지금은 예약할 수 있는 픽업 시간대가 없어요");
    expect(screen.getByText("정원 초과")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "주문서 포기하고 장바구니로" }));

    await waitFor(() => expect(abandonCalled).toBe(true));
    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/cart"));
  });

  it("선점 만료: 화면을 덮는 시트를 보여준다", async () => {
    mockAuthenticatedSession();
    installHold({ status: "EXPIRED", holdRemainingSeconds: 0 });
    installSlots(baseSlots());
    renderPage();

    await screen.findByText("선점 시간이 끝났어요");
  });

  it("오류(선택 직후 정원 소진): 칩을 갱신하고 안내 시트를 보여준다", async () => {
    mockAuthenticatedSession();
    installHold();
    installSlots(baseSlots());
    server.use(
      http.patch(`${BASE}/api/orders/1001/pickup-slot`, () =>
        HttpResponse.json(
          {
            code: "SLOT_FULL",
            message: "정원이 찼어요.",
            serverTime: "2026-08-31T10:00:00+09:00",
          },
          { status: 409 }
        )
      )
    );
    renderPage();

    const chip = await screen.findByRole("button", { name: "19:30~20:00 18/20" });
    await userEvent.click(chip);

    await screen.findByText("방금 정원이 찼어요. 다른 시간대를 골라주세요");
  });

  it("오류(통신): 시간대를 불러오지 못했어요와 다시 시도를 보여주고 재시도하면 복구된다", async () => {
    mockAuthenticatedSession();
    installHold();
    let attempt = 0;
    server.use(
      http.get(`${BASE}/api/orders/1001/pickup-slots`, () => {
        attempt += 1;
        if (attempt === 1) {
          return HttpResponse.json(
            { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T10:00:00+09:00" },
            { status: 500 }
          );
        }
        return HttpResponse.json(baseSlots());
      })
    );
    renderPage();

    await screen.findByText("시간대를 불러오지 못했어요");
    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    await screen.findByRole("button", { name: "19:30~20:00 18/20" });
  });

  it("시간대 선택 후 결제하기로 이동한다", async () => {
    mockAuthenticatedSession();
    installHold();
    installSlots(baseSlots());
    server.use(
      http.patch(`${BASE}/api/orders/1001/pickup-slot`, () =>
        HttpResponse.json({
          orderId: 1001,
          pickupSlotId: 341,
          pickupStartAt: "2026-08-31T19:30:00+09:00",
          pickupEndAt: "2026-08-31T20:00:00+09:00",
          holdRemainingSeconds: 470,
        })
      )
    );
    renderPage();

    const payButtonBefore = await screen.findByRole("button", { name: "결제하기" });
    expect(payButtonBefore).toBeDisabled();

    await userEvent.click(screen.getByRole("button", { name: "19:30~20:00 18/20" }));

    await waitFor(() =>
      expect(screen.getByText("18:30까지 취소할 수 있어요")).toBeInTheDocument()
    );
    const payButton = screen.getByRole("button", { name: "결제하기" });
    expect(payButton).toBeEnabled();

    await userEvent.click(payButton);
    expect(pushMock).toHaveBeenCalledWith("/orders/new/payment?orderId=1001");
  });
});
