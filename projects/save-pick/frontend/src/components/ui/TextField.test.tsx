import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TextField } from "./TextField";

describe("TextField", () => {
  it("라벨과 입력을 연결해서 보여주고 입력을 받는다", async () => {
    const onChange = vi.fn();
    render(<TextField id="email" label="이메일" value="" onChange={onChange} />);

    const input = screen.getByLabelText("이메일");
    await userEvent.type(input, "a");

    expect(onChange).toHaveBeenCalled();
  });

  it("오류가 있으면 필드 아래 캡션으로 보여주고 aria-invalid를 켠다", () => {
    render(
      <TextField
        id="email"
        label="이메일"
        value=""
        onChange={() => {}}
        error="이메일 형식으로 입력해주세요"
      />
    );

    expect(screen.getByText("이메일 형식으로 입력해주세요")).toBeInTheDocument();
    expect(screen.getByLabelText("이메일")).toHaveAttribute("aria-invalid", "true");
  });

  it("오류가 없으면 캡션을 보여주지 않는다", () => {
    render(<TextField id="email" label="이메일" value="" onChange={() => {}} />);

    expect(screen.getByLabelText("이메일")).not.toHaveAttribute("aria-invalid");
  });
});
