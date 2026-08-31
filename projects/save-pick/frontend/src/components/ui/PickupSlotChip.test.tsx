import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { PickupSlotChip } from "./PickupSlotChip";

describe("PickupSlotChip (docs/09-ui-design-brief.md §2.6)", () => {
  it("선택 가능: 활성 버튼이고 클릭하면 onSelect가 호출된다", async () => {
    const onSelect = vi.fn();
    render(
      <PickupSlotChip
        status="selectable"
        timeLabel="20:00~20:30"
        secondaryLabel="4/20"
        onSelect={onSelect}
      />
    );

    const chip = screen.getByRole("button", { name: "20:00~20:30 4/20" });
    expect(chip).toBeEnabled();
    await userEvent.click(chip);
    expect(onSelect).toHaveBeenCalledTimes(1);
  });

  it("선택됨: aria-pressed가 true다", () => {
    render(
      <PickupSlotChip status="selected" timeLabel="20:00~20:30" secondaryLabel="4/20" />
    );

    expect(screen.getByRole("button", { name: "20:00~20:30 4/20" })).toHaveAttribute(
      "aria-pressed",
      "true"
    );
  });

  it.each([
    ["reservationClosed", "마감"],
    ["full", "정원 20/20"],
    ["afterProductClosing", "상품 마감 이후"],
    ["blocked", "운영 중단"],
  ] as const)("%s: 비활성 칩이고 라벨은 그대로 남아 클릭해도 onSelect가 호출되지 않는다", async (status, label) => {
    const onSelect = vi.fn();
    render(
      <PickupSlotChip
        status={status}
        timeLabel="21:00~21:30"
        secondaryLabel={label}
        onSelect={onSelect}
      />
    );

    const chip = screen.getByRole("button", { name: `21:00~21:30 ${label}` });
    expect(chip).toBeDisabled();
    await userEvent.click(chip);
    expect(onSelect).not.toHaveBeenCalled();
  });
});
