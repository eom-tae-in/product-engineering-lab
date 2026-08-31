import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { setServerTimeOffsetMs } from "@/lib/server-time";
import { HoldTimerBar } from "./HoldTimerBar";

describe("HoldTimerBar (docs/09-ui-design-brief.md §2.5)", () => {
  beforeEach(() => {
    setServerTimeOffsetMs(0);
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-31T10:00:00.000Z"));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("10분~1분 초과 구간: 브랜드색과 `선점 시간 09:58 남음` 문구를 보여준다", () => {
    render(<HoldTimerBar holdExpiresAt="2026-08-31T10:09:58.000Z" />);

    act(() => {
      vi.advanceTimersByTime(0);
    });

    const bar = screen.getByRole("status");
    expect(bar).toHaveTextContent("선점 시간 09:58 남음");
    expect(bar.className).toContain("bg-brand-weak");
  });

  it("1분 이하 구간: 경고색과 `MM:SS 남음 · 시간은 연장되지 않아요` 문구로 바뀐다", () => {
    render(<HoldTimerBar holdExpiresAt="2026-08-31T10:01:00.000Z" />);

    act(() => {
      vi.advanceTimersByTime(0);
    });

    const bar = screen.getByRole("status");
    expect(bar).toHaveTextContent("01:00 남음 · 시간은 연장되지 않아요");
    expect(bar.className).toContain("bg-warning-weak");
  });

  it("초 단위로 갱신한다", () => {
    render(<HoldTimerBar holdExpiresAt="2026-08-31T10:01:40.000Z" />);

    act(() => {
      vi.advanceTimersByTime(0);
    });
    expect(screen.getByRole("status")).toHaveTextContent("선점 시간 01:40 남음");

    act(() => {
      vi.advanceTimersByTime(3000);
    });
    expect(screen.getByRole("status")).toHaveTextContent("선점 시간 01:37 남음");
  });

  it("0에 도달하면 스스로 사라지고 onExpire를 한 번 호출한다", () => {
    const onExpire = vi.fn();
    render(<HoldTimerBar holdExpiresAt="2026-08-31T10:00:02.000Z" onExpire={onExpire} />);

    act(() => {
      vi.advanceTimersByTime(0);
    });
    expect(screen.getByRole("status")).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(2000);
    });

    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(onExpire).toHaveBeenCalledTimes(1);
  });

  it("onTick으로 매 초 남은 초를 알려준다", () => {
    const onTick = vi.fn();
    render(<HoldTimerBar holdExpiresAt="2026-08-31T10:00:05.000Z" onTick={onTick} />);

    act(() => {
      vi.advanceTimersByTime(0);
    });
    expect(onTick).toHaveBeenCalledWith(5);

    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(onTick).toHaveBeenCalledWith(4);
  });
});
