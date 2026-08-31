import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AuthProvider } from "@/lib/auth/customer-auth";
import SignupPage from "./page";

const BASE = "http://test.local";
const pushMock = vi.fn();
const replaceMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock, replace: replaceMock }),
}));

function renderPage() {
  return render(
    <AuthProvider>
      <SignupPage />
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

async function fillValidForm() {
  await userEvent.type(await screen.findByLabelText("이메일"), "minsu@example.com");
  await userEvent.type(screen.getByLabelText("비밀번호 (8자 이상)"), "savepick123");
  await userEvent.type(screen.getByLabelText("이름"), "민수");
  await userEvent.type(screen.getByLabelText("휴대폰 번호"), "01012345678");
}

beforeEach(() => {
  window.localStorage.clear();
  pushMock.mockClear();
  replaceMock.mockClear();
});

describe("SC-013 회원가입", () => {
  it("기본 상태: 입력 폼과 안내 문구를 보여준다", async () => {
    mockGuestSession();
    renderPage();

    await screen.findByLabelText("이메일");
    expect(screen.getByLabelText("비밀번호 (8자 이상)")).toBeInTheDocument();
    expect(screen.getByLabelText("이름")).toBeInTheDocument();
    expect(screen.getByLabelText("휴대폰 번호")).toBeInTheDocument();
    expect(screen.getByText("이름과 휴대폰 번호는 매장 픽업 응대에만 써요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "가입하고 계속하기" })).toBeInTheDocument();
  });

  it("오류(필수 누락): 비어 있는 항목마다 개별 문구를 보여준다", async () => {
    mockGuestSession();
    renderPage();

    await screen.findByLabelText("이메일");
    await userEvent.click(screen.getByRole("button", { name: "가입하고 계속하기" }));

    expect(await screen.findByText("이메일 형식으로 입력해주세요")).toBeInTheDocument();
    expect(screen.getByText("비밀번호는 8자 이상이어야 해요")).toBeInTheDocument();
    expect(screen.getByText("이름을 입력해주세요")).toBeInTheDocument();
    expect(screen.getByText("휴대폰 번호를 입력해주세요")).toBeInTheDocument();
  });

  it("오류(형식): 이메일 형식과 비밀번호 길이를 검증한다", async () => {
    mockGuestSession();
    renderPage();

    await userEvent.type(await screen.findByLabelText("이메일"), "jihyun.example.com");
    await userEvent.type(screen.getByLabelText("비밀번호 (8자 이상)"), "1234567");
    await userEvent.type(screen.getByLabelText("이름"), "김지현");
    await userEvent.type(screen.getByLabelText("휴대폰 번호"), "01000000000");
    await userEvent.click(screen.getByRole("button", { name: "가입하고 계속하기" }));

    expect(await screen.findByText("이메일 형식으로 입력해주세요")).toBeInTheDocument();
    expect(screen.getByText("비밀번호는 8자 이상이어야 해요")).toBeInTheDocument();
  });

  it("오류(이메일 중복): 안내 문구와 로그인하기 이동을 보여준다", async () => {
    mockGuestSession();
    server.use(
      http.post(`${BASE}/api/auth/signup`, () =>
        HttpResponse.json(
          {
            code: "EMAIL_DUPLICATED",
            message: "이미 가입된 이메일입니다.",
            serverTime: "2026-08-31T00:00:00+09:00",
          },
          { status: 409 }
        )
      )
    );
    renderPage();

    await fillValidForm();
    await userEvent.click(screen.getByRole("button", { name: "가입하고 계속하기" }));

    expect(await screen.findByText("이미 가입된 이메일이에요")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "로그인하기" })).toBeInTheDocument();
  });

  it("오류(통신): 다시 시도 버튼을 보여준다", async () => {
    mockGuestSession();
    server.use(
      http.post(`${BASE}/api/auth/signup`, () =>
        HttpResponse.json(
          { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T00:00:00+09:00" },
          { status: 500 }
        )
      )
    );
    renderPage();

    await fillValidForm();
    await userEvent.click(screen.getByRole("button", { name: "가입하고 계속하기" }));

    expect(await screen.findByText("가입하지 못했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("가입에 성공하면 별도 로그인 없이 인증 상태가 되어 홈으로 이동한다", async () => {
    mockGuestSession();
    server.use(
      http.post(`${BASE}/api/auth/signup`, () =>
        HttpResponse.json(
          {
            memberId: 42,
            email: "minsu@example.com",
            name: "민수",
            role: "CUSTOMER",
            accessToken: "signup-token",
            accessTokenExpiresAt: "2026-08-31T01:00:00+09:00",
            cartMerged: false,
          },
          { status: 201 }
        )
      )
    );
    renderPage();

    await fillValidForm();
    await userEvent.click(screen.getByRole("button", { name: "가입하고 계속하기" }));

    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/"));
  });
});
