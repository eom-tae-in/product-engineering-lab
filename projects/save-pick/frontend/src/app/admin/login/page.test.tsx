import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AdminAuthProvider } from "@/lib/auth/admin-auth";
import AdminLoginPage from "./page";

const BASE = "http://test.local";
const pushMock = vi.fn();
const replaceMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock, replace: replaceMock }),
}));

function renderPage() {
  return render(
    <AdminAuthProvider>
      <AdminLoginPage />
    </AdminAuthProvider>
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

describe("SC-101 관리자 로그인", () => {
  it("기본 상태: 입력 폼과 안내 문구를 보여준다", async () => {
    mockGuestSession();
    renderPage();

    await screen.findByLabelText("이메일");
    expect(screen.getByLabelText("비밀번호")).toBeInTheDocument();
    expect(screen.getByText("관리자 계정은 운영자가 직접 부여해요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "관리자 로그인" })).toBeInTheDocument();
  });

  it("로딩: 제출 중에는 버튼 라벨이 바뀐다", async () => {
    mockGuestSession();
    server.use(
      http.post(`${BASE}/api/admin/auth/login`, async () => {
        await new Promise((resolve) => setTimeout(resolve, 30));
        return HttpResponse.json({
          memberId: 1,
          name: "상현",
          role: "ADMIN",
          accessToken: "admin-token",
          accessTokenExpiresAt: "2026-08-31T01:00:00+09:00",
        });
      })
    );
    renderPage();

    await userEvent.type(await screen.findByLabelText("이메일"), "owner@savepick.store");
    await userEvent.type(screen.getByLabelText("비밀번호"), "secret");
    await userEvent.click(screen.getByRole("button", { name: "관리자 로그인" }));

    expect(await screen.findByRole("button", { name: "확인하는 중이에요" })).toBeDisabled();

    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/admin"));
  });

  it("오류(인증 실패): 이메일 또는 비밀번호를 확인해달라는 문구를 보여준다", async () => {
    mockGuestSession();
    server.use(
      http.post(`${BASE}/api/admin/auth/login`, () =>
        HttpResponse.json(
          {
            code: "INVALID_CREDENTIALS",
            message: "일치하지 않습니다.",
            serverTime: "2026-08-31T00:00:00+09:00",
          },
          { status: 401 }
        )
      )
    );
    renderPage();

    await userEvent.type(await screen.findByLabelText("이메일"), "owner@savepick.store");
    await userEvent.type(screen.getByLabelText("비밀번호"), "wrong");
    await userEvent.click(screen.getByRole("button", { name: "관리자 로그인" }));

    expect(await screen.findByText("이메일 또는 비밀번호를 확인해주세요")).toBeInTheDocument();
  });

  it("오류(권한 없음): 고객 계정으로 시도하면 FORBIDDEN 문구를 보여주고 관리자 화면으로 넘어가지 않는다", async () => {
    mockGuestSession();
    server.use(
      http.post(`${BASE}/api/admin/auth/login`, () =>
        HttpResponse.json(
          {
            code: "FORBIDDEN",
            message: "관리자 권한이 없습니다.",
            serverTime: "2026-08-31T00:00:00+09:00",
          },
          { status: 403 }
        )
      )
    );
    renderPage();

    await userEvent.type(await screen.findByLabelText("이메일"), "customer@example.com");
    await userEvent.type(screen.getByLabelText("비밀번호"), "savepick123");
    await userEvent.click(screen.getByRole("button", { name: "관리자 로그인" }));

    expect(await screen.findByText("관리자 권한이 없는 계정이에요")).toBeInTheDocument();
    expect(pushMock).not.toHaveBeenCalled();
  });

  it("오류(잠금): 로그인 시도가 많아 잠겼다는 문구를 보여준다", async () => {
    mockGuestSession();
    server.use(
      http.post(`${BASE}/api/admin/auth/login`, () =>
        HttpResponse.json(
          {
            code: "LOGIN_BLOCKED",
            message: "잠겼습니다.",
            serverTime: "2026-08-31T00:00:00+09:00",
            details: { retryAfterAt: "2026-08-31T19:12:00+09:00" },
          },
          { status: 429 }
        )
      )
    );
    renderPage();

    await userEvent.type(await screen.findByLabelText("이메일"), "owner@savepick.store");
    await userEvent.type(screen.getByLabelText("비밀번호"), "secret");
    await userEvent.click(screen.getByRole("button", { name: "관리자 로그인" }));

    expect(await screen.findByText("로그인 시도가 많아 10분간 잠겼어요")).toBeInTheDocument();
  });

  it("로그인에 성공하면 관리자 홈으로 이동한다", async () => {
    mockGuestSession();
    server.use(
      http.post(`${BASE}/api/admin/auth/login`, () =>
        HttpResponse.json({
          memberId: 1,
          name: "상현",
          role: "ADMIN",
          accessToken: "admin-token",
          accessTokenExpiresAt: "2026-08-31T01:00:00+09:00",
        })
      )
    );
    renderPage();

    await userEvent.type(await screen.findByLabelText("이메일"), "owner@savepick.store");
    await userEvent.type(screen.getByLabelText("비밀번호"), "secret");
    await userEvent.click(screen.getByRole("button", { name: "관리자 로그인" }));

    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/admin"));
  });
});
