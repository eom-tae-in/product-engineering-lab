import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Button } from "./Button";

describe("Button", () => {
  it("클릭하면 onClick이 호출된다", async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>주문하기</Button>);

    await userEvent.click(screen.getByRole("button", { name: "주문하기" }));

    expect(onClick).toHaveBeenCalledOnce();
  });

  it("비활성일 때 사유 캡션을 버튼 위에 보여주고 클릭이 막힌다", async () => {
    const onClick = vi.fn();
    render(
      <Button disabled disabledReason="구매할 수 없는 품목을 정리하면 주문할 수 있어요" onClick={onClick}>
        주문하기
      </Button>
    );

    expect(
      screen.getByText("구매할 수 없는 품목을 정리하면 주문할 수 있어요")
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "주문하기" }));
    expect(onClick).not.toHaveBeenCalled();
  });
});
