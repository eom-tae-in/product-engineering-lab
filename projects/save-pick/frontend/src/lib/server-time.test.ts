import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { fetchServerTimeOffsetMs, getServerTimeOffsetMs, nowOnServer, setServerTimeOffsetMs } from "./server-time";

const BASE = "http://test.local";

describe("서버 시각 동기화", () => {
  beforeEach(() => {
    setServerTimeOffsetMs(0);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("서버-클라이언트 시각 차이를 계산한다", async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-31T00:00:00.000Z"));

    server.use(
      http.get(`${BASE}/api/system/time`, () =>
        HttpResponse.json({
          serverTime: "2026-08-31T00:00:05.000Z",
          timezone: "Asia/Seoul",
        })
      )
    );

    const offset = await fetchServerTimeOffsetMs();

    expect(offset).toBe(5000);
  });

  it("nowOnServer는 저장된 offset을 반영한다", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-31T00:00:00.000Z"));
    setServerTimeOffsetMs(3000);

    expect(getServerTimeOffsetMs()).toBe(3000);
    expect(nowOnServer()).toBe(new Date("2026-08-31T00:00:03.000Z").getTime());
  });
});
