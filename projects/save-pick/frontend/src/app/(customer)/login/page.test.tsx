import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AuthProvider } from "@/lib/auth/customer-auth";
import LoginPage from "./page";

const BASE = "http://test.local";
const pushMock = vi.fn();
const replaceMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock, replace: replaceMock }),
}));

function renderPage() {
  return render(
    <AuthProvider>
      <LoginPage />
    </AuthProvider>
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

beforeEach(() => {
  window.localStorage.clear();
  pushMock.mockClear();
  replaceMock.mockClear();
});

describe("SC-012 로그인", () => {
  it("기본 상태: 입력 폼과 안내 문구, 회원가입 이동을 보여준다", async () => {
    mockGuestSession();
    renderPage();

    await screen.findByLabelText("이메일");
    expect(screen.getByLabelText("비밀번호")).toBeInTheDocument();
    expect(screen.getByText("장바구니에 담은 상품은 그대로 유지돼요")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "회원가입" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "로그인" })).toBeInTheDocument();
  });

  it("오류(형식): 이메일 형식이 아니면 필드 아래 문구를 보여주고 요청을 보내지 않는다", async () => {
    mockGuestSession();
    let loginCalled = false;
    server.use(
      http.post(`${BASE}/api/auth/login`, () => {
        loginCalled = true;
        return HttpResponse.json({});
      })
    );
    renderPage();

    await userEvent.type(await screen.findByLabelText("이메일"), "jihyun.example.com");
    await userEvent.type(screen.getByLabelText("비밀번호"), "savepick123");
    await userEvent.click(screen.getByRole("button", { name: "로그인" }));

    expect(await screen.findByText("이메일 형식으로 입력해주세요")).toBeInTheDocument();
    expect(loginCalled).toBe(false);
  });

  it("로딩: 제출 중에는 버튼 라벨이 바뀌고 입력이 비활성화된다", async () => {
    mockGuestSession();
    server.use(
      http.post(`${BASE}/api/auth/login`, async () => {
        await new Promise((resolve) => setTimeout(resolve, 30));
        return HttpResponse.json({
          memberId: 17,
          name: "지현",
          role: "CUSTOMER",
          accessToken: "access-token",
          accessTokenExpiresAt: "2026-08-31T01:00:00+09:00",
          orderPermission: "ALLOWED",
        });
      })
    );
    renderPage();

    await userEvent.type(await screen.findByLabelText("이메일"), "jihyun@example.com");
    await userEvent.type(screen.getByLabelText("비밀번호"), "savepick123");
    await userEvent.click(screen.getByRole("button", { name: "로그인" }));

    expect(await screen.findByRole("button", { name: "로그인하는 중이에요" })).toBeDisabled();
    expect(screen.getByLabelText("이메일")).toBeDisabled();
    expect(screen.getByLabelText("비밀번호")).toBeDisabled();

    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/"));
  });

  it("오류(인증 실패): 어느 쪽이 틀렸는지 구분하지 않는 문구를 보여준다", async () => {
    mockGuestSession();
    server.use(
      http.post(`${BASE}/api/auth/login`, () =>
        HttpResponse.json(
          {
            code: "INVALID_CREDENTIALS",
            message: "이메일 또는 비밀번호가 일치하지 않습니다.",
            serverTime: "2026-08-31T00:00:00+09:00",
          },
          { status: 401 }
        )
      )
    );
    renderPage();

    await userEvent.type(await screen.findByLabelText("이메일"), "jihyun@example.com");
    await userEvent.type(screen.getByLabelText("비밀번호"), "wrongpassword");
    await userEvent.click(screen.getByRole("button", { name: "로그인" }));

    expect(await screen.findByText("이메일 또는 비밀번호를 확인해주세요")).toBeInTheDocument();
  });

  it("오류(연속 5회 실패): 잠금 해제 시각을 포함한 문구를 보여준다", async () => {
    mockGuestSession();
    server.use(
      http.post(`${BASE}/api/auth/login`, () =>
        HttpResponse.json(
          {
            code: "LOGIN_BLOCKED",
            message: "로그인이 잠겼습니다.",
            serverTime: "2026-08-31T00:00:00+09:00",
            details: { retryAfterAt: "2026-08-31T19:12:00+09:00" },
          },
          { status: 429 }
        )
      )
    );
    renderPage();

    await userEvent.type(await screen.findByLabelText("이메일"), "jihyun@example.com");
    await userEvent.type(screen.getByLabelText("비밀번호"), "savepick123");
    await userEvent.click(screen.getByRole("button", { name: "로그인" }));

    expect(
      await screen.findByText("로그인 시도가 많아 10분간 잠겼어요. 19:12 이후 다시 시도해주세요")
    ).toBeInTheDocument();
  });

  it("오류(통신): 다시 시도 버튼을 보여준다", async () => {
    mockGuestSession();
    server.use(
      http.post(`${BASE}/api/auth/login`, () =>
        HttpResponse.json(
          { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T00:00:00+09:00" },
          { status: 500 }
        )
      )
    );
    renderPage();

    await userEvent.type(await screen.findByLabelText("이메일"), "jihyun@example.com");
    await userEvent.type(screen.getByLabelText("비밀번호"), "savepick123");
    await userEvent.click(screen.getByRole("button", { name: "로그인" }));

    expect(await screen.findByText("로그인하지 못했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("로그인에 성공하면 홈으로 이동한다", async () => {
    mockGuestSession();
    server.use(
      http.post(`${BASE}/api/auth/login`, () =>
        HttpResponse.json({
          memberId: 17,
          name: "지현",
          role: "CUSTOMER",
          accessToken: "access-token",
          accessTokenExpiresAt: "2026-08-31T01:00:00+09:00",
          orderPermission: "ALLOWED",
        })
      )
    );
    renderPage();

    await userEvent.type(await screen.findByLabelText("이메일"), "jihyun@example.com");
    await userEvent.type(screen.getByLabelText("비밀번호"), "savepick123");
    await userEvent.click(screen.getByRole("button", { name: "로그인" }));

    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/"));
  });
});
