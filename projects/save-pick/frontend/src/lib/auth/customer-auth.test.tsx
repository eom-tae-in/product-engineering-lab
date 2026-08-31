import { beforeEach, describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AuthProvider, useAuth } from "./customer-auth";

const BASE = "http://test.local";

function Probe() {
  const auth = useAuth();
  return (
    <div>
      <p data-testid="status">{auth.status}</p>
      <p data-testid="name">{auth.user?.name ?? "none"}</p>
      <button
        onClick={() => auth.login("jihyun@example.com", "savepick123")}
      >
        로그인
      </button>
      <button onClick={() => auth.logout()}>로그아웃</button>
    </div>
  );
}

beforeEach(() => {
  window.localStorage.clear();
});

describe("AuthProvider", () => {
  it("리프레시 쿠키가 없으면 guest 상태로 시작한다", async () => {
    server.use(
      http.post(`${BASE}/api/auth/token/refresh`, () =>
        HttpResponse.json(
          { code: "UNAUTHENTICATED", message: "인증이 필요해요.", serverTime: "2026-08-31T00:00:00+09:00" },
          { status: 401 }
        )
      )
    );

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("guest"));
  });

  it("리프레시 쿠키가 유효하면 authenticated 상태로 복원한다", async () => {
    server.use(
      http.post(`${BASE}/api/auth/token/refresh`, () =>
        HttpResponse.json({
          accessToken: "restored-token",
          accessTokenExpiresAt: "2026-08-31T01:00:00+09:00",
        })
      )
    );

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>
    );

    await waitFor(() =>
      expect(screen.getByTestId("status")).toHaveTextContent("authenticated")
    );
  });

  it("로그인에 성공하면 사용자 정보를 채운다", async () => {
    server.use(
      http.post(`${BASE}/api/auth/token/refresh`, () =>
        HttpResponse.json(
          { code: "UNAUTHENTICATED", message: "인증이 필요해요.", serverTime: "2026-08-31T00:00:00+09:00" },
          { status: 401 }
        )
      ),
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

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>
    );

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("guest"));
    await userEvent.click(screen.getByRole("button", { name: "로그인" }));

    await waitFor(() => expect(screen.getByTestId("name")).toHaveTextContent("지현"));
    expect(screen.getByTestId("status")).toHaveTextContent("authenticated");
  });

  it("로그아웃하면 guest 상태로 돌아간다", async () => {
    server.use(
      http.post(`${BASE}/api/auth/token/refresh`, () =>
        HttpResponse.json({
          accessToken: "restored-token",
          accessTokenExpiresAt: "2026-08-31T01:00:00+09:00",
        })
      ),
      http.post(`${BASE}/api/auth/logout`, () => new HttpResponse(null, { status: 204 }))
    );

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>
    );

    await waitFor(() =>
      expect(screen.getByTestId("status")).toHaveTextContent("authenticated")
    );
    await userEvent.click(screen.getByRole("button", { name: "로그아웃" }));

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("guest"));
  });
});
