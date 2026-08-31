import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Skeleton } from "./Skeleton";

describe("Skeleton", () => {
  it("로딩 상태를 접근성 레이블로 알린다", () => {
    render(<Skeleton className="h-4 w-full" />);
    expect(screen.getByRole("status", { name: "불러오는 중" })).toBeInTheDocument();
  });
});
