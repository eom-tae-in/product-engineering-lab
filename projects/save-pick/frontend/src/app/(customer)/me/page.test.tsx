import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AuthProvider } from "@/lib/auth/customer-auth";
import MyPage from "./page";

const BASE = "http://test.local";
const pushMock = vi.fn();
const replaceMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock, replace: replaceMock }),
}));

function renderPage() {
  return render(
    <AuthProvider>
      <MyPage />
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
        {
          code: "UNAUTHENTICATED",
          message: "인증이 필요해요.",
          serverTime: "2026-08-31T00:00:00+09:00",
        },
        { status: 401 }
      )
    )
  );
}

function mockMe(overrides: Partial<Record<string, unknown>> = {}) {
  server.use(
    http.get(`${BASE}/api/me`, () =>
      HttpResponse.json({
        memberId: 17,
        email: "jihyun@example.com",
        name: "지현",
        phone: "01098765432",
        orderPermission: "ALLOWED",
        ...overrides,
      })
    )
  );
}

function mockNoShowStatus(overrides: Partial<Record<string, unknown>> = {}) {
  server.use(
    http.get(`${BASE}/api/me/no-show-status`, () =>
      HttpResponse.json({
        recentNoShowCount: 0,
        windowDays: 30,
        orderPermission: "ALLOWED",
        restrictedUntil: null,
        noShowOrders: [],
        ...overrides,
      })
    )
  );
}

beforeEach(() => {
  window.localStorage.clear();
  pushMock.mockClear();
  replaceMock.mockClear();
});

describe("SC-014 마이페이지", () => {
  it("미로그인 상태로 진입하면 로그인 화면으로 보낸다", async () => {
    mockGuestSession();
    renderPage();

    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/login"));
  });

  it("기본 상태: 회원 정보와 노쇼 횟수를 보여준다", async () => {
    mockAuthenticatedSession();
    mockMe();
    mockNoShowStatus({ recentNoShowCount: 1 });
    renderPage();

    expect(await screen.findByText("지현")).toBeInTheDocument();
    expect(screen.getByText("jihyun@example.com")).toBeInTheDocument();
    expect(screen.getByText("01098765432")).toBeInTheDocument();
    expect(screen.getByText("1회")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "수정" })).toBeInTheDocument();
  });

  it("주문 제한: 상단 경고 카드를 보여준다", async () => {
    mockAuthenticatedSession();
    mockMe();
    mockNoShowStatus({
      recentNoShowCount: 3,
      orderPermission: "RESTRICTED",
      restrictedUntil: "2026-09-04T19:00:00+09:00",
    });
    renderPage();

    expect(
      await screen.findByText("노쇼 3회 누적 · 2026-09-04 19:00까지 새 주문을 만들 수 없어요")
    ).toBeInTheDocument();
    expect(screen.getByText("확정된 주문은 그대로 유지돼요")).toBeInTheDocument();
  });

  it("수정 모드: 이름·휴대폰은 활성, 이메일은 비활성이며 안내 문구를 보여준다", async () => {
    mockAuthenticatedSession();
    mockMe();
    mockNoShowStatus();
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "수정" }));

    expect(screen.getByLabelText("이름")).not.toBeDisabled();
    expect(screen.getByLabelText("휴대폰 번호")).not.toBeDisabled();
    expect(screen.getByLabelText("이메일")).toBeDisabled();
    expect(screen.getByText("이메일은 변경할 수 없어요")).toBeInTheDocument();
  });

  it("오류(형식): 휴대폰 번호 형식이 아니면 저장하지 않는다", async () => {
    mockAuthenticatedSession();
    mockMe();
    mockNoShowStatus();
    let patchCalled = false;
    server.use(
      http.patch(`${BASE}/api/me`, () => {
        patchCalled = true;
        return HttpResponse.json({});
      })
    );
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "수정" }));
    const phoneInput = screen.getByLabelText("휴대폰 번호");
    await userEvent.clear(phoneInput);
    await userEvent.type(phoneInput, "010-00");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(await screen.findByText("휴대폰 번호 형식을 확인해주세요")).toBeInTheDocument();
    expect(patchCalled).toBe(false);
  });

  it("수정 저장에 성공하면 기본 상태로 돌아가 갱신된 값을 보여준다", async () => {
    mockAuthenticatedSession();
    mockMe();
    mockNoShowStatus();
    server.use(
      http.patch(`${BASE}/api/me`, () =>
        HttpResponse.json({
          memberId: 17,
          email: "jihyun@example.com",
          name: "김지현",
          phone: "01011112222",
        })
      )
    );
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "수정" }));
    const nameInput = screen.getByLabelText("이름");
    await userEvent.clear(nameInput);
    await userEvent.type(nameInput, "김지현");
    const phoneInput = screen.getByLabelText("휴대폰 번호");
    await userEvent.clear(phoneInput);
    await userEvent.type(phoneInput, "01011112222");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(await screen.findByText("김지현")).toBeInTheDocument();
    expect(screen.getByText("01011112222")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "저장" })).not.toBeInTheDocument();
  });

  it("오류(통신): 저장하지 못했어요 문구와 다시 시도 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    mockMe();
    mockNoShowStatus();
    server.use(
      http.patch(`${BASE}/api/me`, () =>
        HttpResponse.json(
          { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T00:00:00+09:00" },
          { status: 500 }
        )
      )
    );
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "수정" }));
    const phoneInput = screen.getByLabelText("휴대폰 번호");
    await userEvent.clear(phoneInput);
    await userEvent.type(phoneInput, "01011112222");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(await screen.findByText("저장하지 못했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("오류(조회 실패): 다시 시도 버튼을 누르면 다시 불러온다", async () => {
    mockAuthenticatedSession();
    let attempt = 0;
    server.use(
      http.get(`${BASE}/api/me`, () => {
        attempt += 1;
        if (attempt === 1) {
          return HttpResponse.json(
            { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T00:00:00+09:00" },
            { status: 500 }
          );
        }
        return HttpResponse.json({
          memberId: 17,
          email: "jihyun@example.com",
          name: "지현",
          phone: "01098765432",
          orderPermission: "ALLOWED",
        });
      })
    );
    mockNoShowStatus();
    renderPage();

    const retryButton = await screen.findByRole("button", { name: "다시 시도" });
    await userEvent.click(retryButton);

    expect(await screen.findByText("지현")).toBeInTheDocument();
  });

  it("로그아웃하면 로그인 화면으로 이동한다", async () => {
    mockAuthenticatedSession();
    mockMe();
    mockNoShowStatus();
    server.use(http.post(`${BASE}/api/auth/logout`, () => new HttpResponse(null, { status: 204 })));
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "로그아웃" }));

    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/login"));
  });
});
