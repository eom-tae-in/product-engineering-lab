import { describe, expect, it } from "vitest";
import { formatKstDateTime, formatKstTime } from "./format";

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
