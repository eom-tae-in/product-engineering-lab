import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import {
  fetchServerTimeOffsetMs,
  getServerTimeOffsetMs,
  kstDateString,
  nowOnServer,
  setServerTimeOffsetMs,
} from "./server-time";

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

  it("kstDateString은 로컬 타임존과 무관하게 KST 기준 오늘 날짜를 반환한다", () => {
    // UTC 15:30 = KST 다음날 00:30. 로컬 자정 기준으로 잘못 계산하면 하루 어긋난다.
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-28T15:30:00.000Z"));

    expect(kstDateString()).toBe("2026-08-29");
  });

  it("kstDateString(1)은 내일(KST) 날짜를 반환한다", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-28T00:00:00.000Z"));

    expect(kstDateString(1)).toBe("2026-08-29");
  });

  it("kstDateString은 offset을 반영한다", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-28T14:59:00.000Z"));
    // offset 2분: KST 23:59 -> 다음날 00:01(KST)로 넘어간다.
    setServerTimeOffsetMs(2 * 60 * 1000);

    expect(kstDateString()).toBe("2026-08-29");
  });
});
