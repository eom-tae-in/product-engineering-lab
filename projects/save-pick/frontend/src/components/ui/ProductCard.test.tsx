import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { ProductCard, type ProductCardData } from "./ProductCard";

function baseProduct(overrides: Partial<ProductCardData> = {}): ProductCardData {
  return {
    productId: 12,
    name: "국내산 삼겹살 300g",
    originalPrice: 12000,
    discountRate: 30,
    discountPrice: 8400,
    availableQuantity: 8,
    lowStock: false,
    soldOut: false,
    closingAt: "2026-08-28T21:00:00+09:00",
    ...overrides,
  };
}

describe("ProductCard", () => {
  it("docs/09 §2.4 6개 정보를 보여준다: 정가·할인가·할인율·절약액·잔여 수량·마감 시각", () => {
    render(<ProductCard product={baseProduct()} />);

    expect(screen.getByText("국내산 삼겹살 300g")).toBeInTheDocument();
    expect(screen.getByText("12,000원")).toBeInTheDocument();
    expect(screen.getByText("8,400원")).toBeInTheDocument();
    expect(screen.getByText("30% 할인")).toBeInTheDocument();
    expect(screen.getByText("3,600원 아낌")).toBeInTheDocument();
    expect(screen.getByText("남은 수량 8개")).toBeInTheDocument();
    expect(screen.getByText("오늘 21:00 마감")).toBeInTheDocument();
  });

  it("상품 상세로 이동하는 링크를 만든다", () => {
    render(<ProductCard product={baseProduct()} />);
    expect(screen.getByRole("link")).toHaveAttribute("href", "/products/12");
  });

  it("소진 임박(5개 이하)이면 소진 임박 문구를 함께 보여준다", () => {
    render(<ProductCard product={baseProduct({ availableQuantity: 3, lowStock: true })} />);
    expect(screen.getByText("남은 수량 3개 · 소진 임박")).toBeInTheDocument();
  });

  it("품절이면 품절 배지와 품절됐어요 문구를 보여주고 카드를 흐리게 한다", () => {
    render(
      <ProductCard
        product={baseProduct({ soldOut: true, availableQuantity: 0, lowStock: true })}
      />
    );
    expect(screen.getByText("품절")).toBeInTheDocument();
    expect(screen.getByText("품절됐어요")).toBeInTheDocument();
    expect(screen.getByRole("link")).toHaveClass("opacity-[0.55]");
  });

  it("할인율 0%이면 정가 취소선과 할인 배지를 표시하지 않는다", () => {
    render(
      <ProductCard
        product={baseProduct({ discountRate: 0, discountPrice: 12000 })}
      />
    );
    expect(screen.queryByText("12,000원", { selector: ".line-through" })).not.toBeInTheDocument();
    expect(screen.queryByText("30% 할인")).not.toBeInTheDocument();
    expect(screen.queryByText(/아낌/)).not.toBeInTheDocument();
  });
});
