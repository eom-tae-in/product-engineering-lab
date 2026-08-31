import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Badge } from "./Badge";
import { OrderStatusBadge } from "./OrderStatusBadge";

describe("Badge", () => {
  it("전달한 내용을 그대로 보여준다", () => {
    render(<Badge tone="discount">30% 할인</Badge>);
    expect(screen.getByText("30% 할인")).toBeInTheDocument();
  });
});

describe("OrderStatusBadge", () => {
  it("PENDING은 고객 화면 기본 목록에 노출하지 않는다 (docs/06 SC-009)", () => {
    const { container } = render(<OrderStatusBadge status="PENDING" audience="customer" />);
    expect(container).toBeEmptyDOMElement();
  });

  it("PENDING은 관리자 화면에서 결제 대기로 보인다", () => {
    render(<OrderStatusBadge status="PENDING" audience="admin" />);
    expect(screen.getByText("결제 대기")).toBeInTheDocument();
  });

  it("CONFIRMED는 고객·관리자 모두 확정으로 보인다", () => {
    render(<OrderStatusBadge status="CONFIRMED" />);
    expect(screen.getByText("확정")).toBeInTheDocument();
  });

  it("NO_SHOW는 노쇼로 보인다", () => {
    render(<OrderStatusBadge status="NO_SHOW" />);
    expect(screen.getByText("노쇼")).toBeInTheDocument();
  });
});
