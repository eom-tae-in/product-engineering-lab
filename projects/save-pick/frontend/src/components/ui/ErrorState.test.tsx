import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ErrorState } from "./ErrorState";

describe("ErrorState", () => {
  it("다시 시도 버튼을 누르면 onRetry가 호출된다", async () => {
    const onRetry = vi.fn();
    render(<ErrorState message="상품을 불러오지 못했어요" onRetry={onRetry} />);

    expect(screen.getByText("상품을 불러오지 못했어요")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it("onRetry가 없으면 다시 시도 버튼을 보여주지 않는다", () => {
    render(<ErrorState message="조회할 수 없는 주문이에요" />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });
});
