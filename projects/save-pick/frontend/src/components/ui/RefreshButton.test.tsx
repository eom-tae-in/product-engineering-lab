import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RefreshButton } from "./RefreshButton";

const refreshMock = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: refreshMock }),
}));

describe("RefreshButton", () => {
  it("클릭하면 router.refresh()를 호출한다", async () => {
    render(<RefreshButton />);

    const button = screen.getByRole("button", { name: "다시 시도" });
    await userEvent.click(button);

    expect(refreshMock).toHaveBeenCalledOnce();
  });

  it("label을 지정하면 그 문구를 보여준다", () => {
    render(<RefreshButton label="새로고침" />);
    expect(screen.getByRole("button", { name: "새로고침" })).toBeInTheDocument();
  });
});
