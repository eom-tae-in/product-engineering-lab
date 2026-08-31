import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QuantityStepper } from "./QuantityStepper";

describe("QuantityStepper", () => {
  it("+ 를 누르면 다음 값으로 onChange를 호출한다", async () => {
    const onChange = vi.fn();
    render(<QuantityStepper value={2} max={5} onChange={onChange} />);

    await userEvent.click(screen.getByRole("button", { name: "수량 늘리기" }));

    expect(onChange).toHaveBeenCalledWith(3);
  });

  it("− 를 누르면 이전 값으로 onChange를 호출한다", async () => {
    const onChange = vi.fn();
    render(<QuantityStepper value={2} max={5} onChange={onChange} />);

    await userEvent.click(screen.getByRole("button", { name: "수량 줄이기" }));

    expect(onChange).toHaveBeenCalledWith(1);
  });

  it("최댓값에 도달하면 + 버튼만 비활성된다", () => {
    render(<QuantityStepper value={5} max={5} onChange={vi.fn()} />);

    expect(screen.getByRole("button", { name: "수량 늘리기" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "수량 줄이기" })).toBeEnabled();
  });

  it("최솟값(min=0)에 도달하면 − 버튼만 비활성된다", () => {
    render(<QuantityStepper value={0} min={0} max={5} onChange={vi.fn()} />);

    expect(screen.getByRole("button", { name: "수량 줄이기" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "수량 늘리기" })).toBeEnabled();
  });

  it("disabled면 두 버튼 모두 비활성된다", () => {
    render(<QuantityStepper value={2} max={5} onChange={vi.fn()} disabled />);

    expect(screen.getByRole("button", { name: "수량 줄이기" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "수량 늘리기" })).toBeDisabled();
  });

  it("커스텀 라벨을 적용할 수 있다", () => {
    render(
      <QuantityStepper
        value={1}
        max={5}
        onChange={vi.fn()}
        decreaseLabel="삼겹살 수량 줄이기"
        increaseLabel="삼겹살 수량 늘리기"
      />
    );

    expect(screen.getByRole("button", { name: "삼겹살 수량 줄이기" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "삼겹살 수량 늘리기" })).toBeInTheDocument();
  });
});
