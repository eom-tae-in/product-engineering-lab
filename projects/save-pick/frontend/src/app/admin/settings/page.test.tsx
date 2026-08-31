import { beforeEach, describe, expect, it } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AdminAuthProvider } from "@/lib/auth/admin-auth";
import AdminSettingsPage from "./page";

const BASE = "http://test.local";

function renderPage() {
  return render(
    <AdminAuthProvider>
      <AdminSettingsPage />
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

function mockSettings(overrides: Partial<Record<string, unknown>> = {}) {
  server.use(
    http.get(`${BASE}/api/admin/store-settings`, () =>
      HttpResponse.json({
        name: "savePick 신선마켓",
        address: "서울특별시 ○○구 ○○로 12",
        phone: "0212345678",
        openTime: "10:00",
        closeTime: "22:00",
        slotUnitMinutes: 30,
        defaultSlotCapacity: 20,
        holidays: ["2026-09-01"],
        ...overrides,
      })
    )
  );
}

beforeEach(() => {
  window.localStorage.clear();
});

describe("SC-113 픽업 운영 설정", () => {
  it("기본 상태: 영업시간·정원·휴무일 값을 보여준다", async () => {
    mockAuthenticatedSession();
    mockSettings();
    renderPage();

    expect(await screen.findByDisplayValue("10:00")).toBeInTheDocument();
    expect(screen.getByDisplayValue("22:00")).toBeInTheDocument();
    expect(screen.getByDisplayValue("20")).toBeInTheDocument();
    expect(screen.getByText("2026-09-01")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "저장" })).toBeInTheDocument();
  });

  it("로딩 상태: 설정 값을 불러오는 동안 스켈레톤을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/store-settings`, async () => {
        await new Promise((resolve) => setTimeout(resolve, 30));
        return HttpResponse.json({
          name: "savePick 신선마켓",
          address: "서울특별시 ○○구 ○○로 12",
          phone: "0212345678",
          openTime: "10:00",
          closeTime: "22:00",
          slotUnitMinutes: 30,
          defaultSlotCapacity: 20,
          holidays: [],
        });
      })
    );
    renderPage();

    expect(screen.getAllByRole("status", { name: "불러오는 중" }).length).toBeGreaterThan(0);
    await waitFor(() => expect(screen.getByDisplayValue("10:00")).toBeInTheDocument());
  });

  it("오류(통신): 불러오지 못했어요 문구와 다시 시도 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    let attempt = 0;
    server.use(
      http.get(`${BASE}/api/admin/store-settings`, () => {
        attempt += 1;
        if (attempt === 1) {
          return HttpResponse.json(
            {
              code: "INTERNAL_ERROR",
              message: "서버 오류",
              serverTime: "2026-08-31T00:00:00+09:00",
            },
            { status: 500 }
          );
        }
        return HttpResponse.json({
          name: "savePick 신선마켓",
          address: "서울특별시 ○○구 ○○로 12",
          phone: "0212345678",
          openTime: "10:00",
          closeTime: "22:00",
          slotUnitMinutes: 30,
          defaultSlotCapacity: 20,
          holidays: [],
        });
      })
    );
    renderPage();

    expect(await screen.findByText("불러오지 못했어요")).toBeInTheDocument();
    const retryButton = screen.getByRole("button", { name: "다시 시도" });

    await userEvent.click(retryButton);
    expect(await screen.findByDisplayValue("10:00")).toBeInTheDocument();
  });

  it("오류(시간 역전): 종료 시각이 시작 시각보다 빠르면 저장을 막는다", async () => {
    mockAuthenticatedSession();
    mockSettings();
    let putCalled = false;
    server.use(
      http.put(`${BASE}/api/admin/store-settings`, () => {
        putCalled = true;
        return HttpResponse.json({});
      })
    );
    renderPage();

    const closeInput = await screen.findByLabelText("종료");
    fireEvent.change(closeInput, { target: { value: "09:00" } });
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(
      await screen.findByText("영업 종료 시각은 시작 시각보다 늦어야 해요")
    ).toBeInTheDocument();
    expect(putCalled).toBe(false);
  });

  it("오류(정원 값): 정원이 1 미만이면 저장을 막는다", async () => {
    mockAuthenticatedSession();
    mockSettings();
    let putCalled = false;
    server.use(
      http.put(`${BASE}/api/admin/store-settings`, () => {
        putCalled = true;
        return HttpResponse.json({});
      })
    );
    renderPage();

    const capacityInput = await screen.findByLabelText("시간대당 예약 정원 (건)");
    fireEvent.change(capacityInput, { target: { value: "0" } });
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(await screen.findByText("정원은 1 이상 정수여야 해요")).toBeInTheDocument();
    expect(putCalled).toBe(false);
  });

  it("휴무일을 추가하고 삭제할 수 있다", async () => {
    mockAuthenticatedSession();
    mockSettings({ holidays: [] });
    renderPage();

    const dateInput = await screen.findByLabelText("휴무일 날짜");
    fireEvent.change(dateInput, { target: { value: "2026-09-05" } });
    await userEvent.click(screen.getByRole("button", { name: "휴무일 추가" }));

    expect(await screen.findByText("2026-09-05")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "삭제" }));
    expect(screen.queryByText("2026-09-05")).not.toBeInTheDocument();
  });

  it("저장에 성공하면 제외된 시간대·유지된 확정 주문 요약을 보여준다", async () => {
    mockAuthenticatedSession();
    mockSettings();
    server.use(
      http.put(`${BASE}/api/admin/store-settings`, () =>
        HttpResponse.json({
          openTime: "10:00",
          closeTime: "21:00",
          defaultSlotCapacity: 20,
          holidays: ["2026-09-01"],
          excludedFutureSlotCount: 2,
          keptConfirmedOrderCount: 3,
          appliedFrom: "2026-08-31T09:50:00+09:00",
        })
      )
    );
    renderPage();

    const closeInput = await screen.findByLabelText("종료");
    fireEvent.change(closeInput, { target: { value: "21:00" } });
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(
      await screen.findByText(
        "설정을 저장했어요 · 제외된 미래 시간대 2개, 유지된 확정 주문 3건"
      )
    ).toBeInTheDocument();
  });

  it("저장 통신 오류: 기본 오류 문구와 다시 시도 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    mockSettings();
    server.use(
      http.put(`${BASE}/api/admin/store-settings`, () =>
        HttpResponse.json(
          {
            code: "INTERNAL_ERROR",
            message: "서버 오류",
            serverTime: "2026-08-31T00:00:00+09:00",
          },
          { status: 500 }
        )
      )
    );
    renderPage();

    await screen.findByDisplayValue("10:00");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(
      await screen.findByText(
        "일시적인 오류로 처리하지 못했어요. 잠시 뒤 다시 시도해주세요."
      )
    ).toBeInTheDocument();
  });
});
