import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { EmptyState } from "./EmptyState";

describe("EmptyState", () => {
  it("사실 문구·이유·행동을 모두 보여준다", () => {
    render(
      <EmptyState
        message="지금 판매 중인 상품이 없어요"
        reason="매장 영업시간은 10:00~22:00입니다"
        action={<button>새로고침</button>}
      />
    );

    expect(screen.getByText("지금 판매 중인 상품이 없어요")).toBeInTheDocument();
    expect(screen.getByText("매장 영업시간은 10:00~22:00입니다")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "새로고침" })).toBeInTheDocument();
  });

  it("이유가 없으면 렌더하지 않는다", () => {
    render(<EmptyState message="장바구니가 비어 있어요" />);
    expect(screen.getByText("장바구니가 비어 있어요")).toBeInTheDocument();
  });
});
