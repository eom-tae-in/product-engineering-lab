import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { BottomSheet } from "./BottomSheet";

describe("BottomSheet", () => {
  it("open이 false면 아무것도 렌더링하지 않는다", () => {
    const { container } = render(
      <BottomSheet open={false}>
        <p>내용</p>
      </BottomSheet>
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("open이 true면 내용을 보여준다", () => {
    render(
      <BottomSheet open>
        <p>내용</p>
      </BottomSheet>
    );
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("내용")).toBeInTheDocument();
  });

  it("dismissible 기본값에서 딤을 탭하면 onClose를 호출한다", async () => {
    const onClose = vi.fn();
    render(
      <BottomSheet open onClose={onClose}>
        <p>내용</p>
      </BottomSheet>
    );

    await userEvent.click(screen.getByRole("button", { name: "닫기" }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("dismissible이 false면 딤을 탭해도 onClose를 호출하지 않는다", async () => {
    const onClose = vi.fn();
    render(
      <BottomSheet open onClose={onClose} dismissible={false}>
        <p>내용</p>
      </BottomSheet>
    );

    await userEvent.click(screen.getByRole("button", { name: "닫기" }));
    expect(onClose).not.toHaveBeenCalled();
  });
});
