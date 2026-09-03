import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { formatKstClosing, formatKstDateTime, formatKstTime, formatWon } from "./format";
import { setServerTimeOffsetMs } from "./server-time";

describe("formatKstTime", () => {
  it("ISO 문자열에서 HH:MM만 잘라낸다", () => {
    expect(formatKstTime("2026-08-31T19:12:00+09:00")).toBe("19:12");
  });
});

describe("formatKstDateTime", () => {
  it("ISO 문자열에서 YYYY-MM-DD HH:MM을 잘라낸다", () => {
    expect(formatKstDateTime("2026-09-04T19:00:00+09:00")).toBe("2026-09-04 19:00");
  });
});

describe("formatKstClosing", () => {
  beforeEach(() => {
    setServerTimeOffsetMs(0);
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-09-02T11:00:00.000Z")); // KST 2026-09-02 20:00
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("같은 날 마감이면 오늘로 부른다", () => {
    expect(formatKstClosing("2026-09-02T21:15:00+09:00")).toBe("오늘 21:15");
  });

  // BR-003은 마감 시각의 "시각"만 영업 종료 이내면 되게 해 다음 날 마감 상품이 실제로
  // 존재한다. 그때 "오늘"을 붙이면 사실과 다른 정보가 된다.
  it("다음 날 마감이면 내일로 부른다", () => {
    expect(formatKstClosing("2026-09-03T20:00:00+09:00")).toBe("내일 20:00");
  });

  it("이틀 뒤부터는 날짜로 보여준다", () => {
    expect(formatKstClosing("2026-09-05T20:00:00+09:00")).toBe("9월 5일 20:00");
  });

  it("KST 자정 직전에도 브라우저 타임존과 무관하게 같은 날로 본다", () => {
    vi.setSystemTime(new Date("2026-09-02T14:30:00.000Z")); // KST 2026-09-02 23:30
    expect(formatKstClosing("2026-09-02T21:15:00+09:00")).toBe("오늘 21:15");
    expect(formatKstClosing("2026-09-03T20:00:00+09:00")).toBe("내일 20:00");
  });
});

describe("formatWon", () => {
  it("천 단위 콤마와 원을 붙인다", () => {
    expect(formatWon(12000)).toBe("12,000원");
  });

  it("0원도 그대로 표기한다", () => {
    expect(formatWon(0)).toBe("0원");
  });
});
