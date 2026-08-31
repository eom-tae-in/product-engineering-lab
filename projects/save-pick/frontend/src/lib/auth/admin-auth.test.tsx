import { describe, expect, it } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AdminAuthProvider, useAdminAuth } from "./admin-auth";

const BASE = "http://test.local";

function Probe() {
  const auth = useAdminAuth();
  return (
    <div>
      <p data-testid="status">{auth.status}</p>
      <button onClick={() => auth.login("owner@savepick.store", "secret")}>로그인</button>
    </div>
  );
}

describe("AdminAuthProvider", () => {
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
      <AdminAuthProvider>
        <Probe />
      </AdminAuthProvider>
    );

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("guest"));
  });

  it("관리자 전용 로그인 경로(API-101)로 로그인한다", async () => {
    server.use(
      http.post(`${BASE}/api/auth/token/refresh`, () =>
        HttpResponse.json(
          { code: "UNAUTHENTICATED", message: "인증이 필요해요.", serverTime: "2026-08-31T00:00:00+09:00" },
          { status: 401 }
        )
      ),
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

    render(
      <AdminAuthProvider>
        <Probe />
      </AdminAuthProvider>
    );

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("guest"));
    await userEvent.click(screen.getByRole("button", { name: "로그인" }));

    await waitFor(() =>
      expect(screen.getByTestId("status")).toHaveTextContent("authenticated")
    );
  });
});
