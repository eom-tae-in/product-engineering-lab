import { beforeEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import {
  ApiError,
  clientRequest,
  getStoredGuestToken,
  registerTokenProvider,
  serverGet,
} from "./api-client";

const BASE = "http://test.local";

beforeEach(() => {
  window.localStorage.clear();
});

describe("serverGet", () => {
  it("성공 응답 본문을 그대로 돌려준다", async () => {
    server.use(
      http.get(`${BASE}/api/store`, () => HttpResponse.json({ name: "savePick 강남점" }))
    );

    const result = await serverGet<{ name: string }>("/api/store");

    expect(result).toEqual({ name: "savePick 강남점" });
  });

  it("오류 응답을 ApiError로 던진다", async () => {
    server.use(
      http.get(`${BASE}/api/store`, () =>
        HttpResponse.json(
          { code: "NOT_FOUND", message: "매장을 찾을 수 없어요.", serverTime: "2026-08-31T00:00:00+09:00" },
          { status: 404 }
        )
      )
    );

    await expect(serverGet("/api/store")).rejects.toMatchObject({
      code: "NOT_FOUND",
      status: 404,
    });
  });

  it("응답 헤더의 X-Guest-Token을 localStorage에 저장한다", async () => {
    server.use(
      http.post(`${BASE}/api/cart/items`, () =>
        HttpResponse.json(
          { cartItemId: 1 },
          { headers: { "X-Guest-Token": "guest-abc" } }
        )
      )
    );

    await clientRequest("/api/cart/items", { method: "POST", useGuestToken: true });

    expect(getStoredGuestToken()).toBe("guest-abc");
  });
});

describe("clientRequest 401 재발급", () => {
  it("401을 받으면 재발급을 한 번 시도하고 새 토큰으로 재요청한다", async () => {
    let callCount = 0;
    server.use(
      http.get(`${BASE}/api/me`, ({ request }) => {
        callCount += 1;
        const auth = request.headers.get("authorization");
        if (auth === "Bearer new-token") {
          return HttpResponse.json({ memberId: 1, name: "지현" });
        }
        return HttpResponse.json(
          { code: "UNAUTHENTICATED", message: "인증이 필요해요.", serverTime: "2026-08-31T00:00:00+09:00" },
          { status: 401 }
        );
      })
    );

    const refresh = vi.fn().mockResolvedValue("new-token");
    registerTokenProvider("customer", { getAccessToken: () => "old-token", refresh });

    const result = await clientRequest<{ memberId: number; name: string }>("/api/me", {
      authScope: "customer",
    });

    expect(result).toEqual({ memberId: 1, name: "지현" });
    expect(refresh).toHaveBeenCalledOnce();
    expect(callCount).toBe(2);
  });

  it("재발급도 실패하면 원래 오류를 그대로 던진다", async () => {
    server.use(
      http.get(`${BASE}/api/me`, () =>
        HttpResponse.json(
          { code: "UNAUTHENTICATED", message: "인증이 필요해요.", serverTime: "2026-08-31T00:00:00+09:00" },
          { status: 401 }
        )
      )
    );

    const refresh = vi.fn().mockResolvedValue(null);
    registerTokenProvider("customer", { getAccessToken: () => "old-token", refresh });

    await expect(
      clientRequest("/api/me", { authScope: "customer" })
    ).rejects.toBeInstanceOf(ApiError);
    expect(refresh).toHaveBeenCalledOnce();
  });
});
