import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import ProductDetailPage from "./page";
import ProductDetailLoading from "./loading";
import { setServerTimeOffsetMs } from "@/lib/server-time";

const BASE = "http://test.local";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: vi.fn() }),
}));

function sampleDetail(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    serverTime: "2026-08-31T10:00:00+09:00",
    productId: 12,
    name: "국내산 삼겹살 300g",
    description: "오늘 손질한 국내산 삼겹살입니다.",
    saleUnit: "300g",
    originalPrice: 12000,
    discountRate: 30,
    discountPrice: 8400,
    availableQuantity: 8,
    lowStock: false,
    soldOut: false,
    maxOrderQuantity: 5,
    closingAt: "2026-08-31T21:00:00+09:00",
    purchasable: true,
    ...overrides,
  };
}

function mockDetail(body: Record<string, unknown>, status = 200) {
  server.use(http.get(`${BASE}/api/products/12`, () => HttpResponse.json(body, { status })));
}

async function renderDetail() {
  const page = await ProductDetailPage({
    params: Promise.resolve({ id: "12" }),
    searchParams: Promise.resolve({}),
  });
  return render(page);
}

describe("SC-003 상품 상세", () => {
  // 마감 표기가 "오늘/내일/날짜"로 갈리므로(formatKstClosing) 픽스처의 마감일이 항상
  // "오늘"이 되도록 시각을 고정한다. 고정하지 않으면 실행하는 날짜에 따라 결과가 바뀐다.
  beforeEach(() => {
    setServerTimeOffsetMs(0);
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date("2026-08-31T02:00:00.000Z")); // KST 2026-08-31 11:00
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("기본 상태: 9개 표시 정보와 담기 버튼을 보여준다", async () => {
    mockDetail(sampleDetail());

    await renderDetail();

    expect(screen.getByText("국내산 삼겹살 300g")).toBeInTheDocument();
    expect(screen.getByText("오늘 손질한 국내산 삼겹살입니다.")).toBeInTheDocument();
    expect(screen.getByText("300g")).toBeInTheDocument();
    expect(screen.getByText("12,000원")).toBeInTheDocument();
    expect(screen.getByText("8,400원")).toBeInTheDocument();
    expect(screen.getByText("30% 할인")).toBeInTheDocument();
    expect(screen.getByText("남은 수량 8개")).toBeInTheDocument();
    expect(screen.getByText("오늘 21:00 마감")).toBeInTheDocument();
    expect(screen.getByText("1회 최대 5개")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "장바구니 담기" })).toBeEnabled();
  });

  it("로딩 상태: 이미지·텍스트 스켈레톤을 보여준다", () => {
    render(<ProductDetailLoading />);
    expect(screen.getAllByRole("status", { name: "불러오는 중" }).length).toBeGreaterThan(0);
  });

  it("재고 소진: 품절됐어요 문구와 지금은 담을 수 없어요 안내, 비활성 버튼을 보여준다", async () => {
    mockDetail(sampleDetail({ soldOut: true, availableQuantity: 0, purchasable: false }));

    await renderDetail();

    expect(screen.getByText("품절됐어요")).toBeInTheDocument();
    expect(screen.getByText("지금은 담을 수 없어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "장바구니 담기" })).toBeDisabled();
  });

  it("오류(없는 상품): 상품을 찾을 수 없어요 문구와 상품 목록으로 버튼을 보여준다", async () => {
    mockDetail(
      { code: "NOT_FOUND", message: "없는 상품", serverTime: "2026-08-31T00:00:00+09:00" },
      404
    );

    await renderDetail();

    expect(screen.getByText("상품을 찾을 수 없어요")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "상품 목록으로" })).toHaveAttribute("href", "/");
  });

  it("마감 도달: 판매가 종료된 상품이에요 안내를 마감 시각과 함께 보여준다", async () => {
    mockDetail(
      {
        code: "PRODUCT_CLOSED",
        message: "판매가 종료됐어요",
        serverTime: "2026-08-31T21:05:00+09:00",
        details: { name: "국내산 삼겹살 300g", closingAt: "2026-08-31T21:00:00+09:00" },
      },
      409
    );

    await renderDetail();

    expect(
      screen.getByText("판매가 종료된 상품이에요 (21:00 마감)")
    ).toBeInTheDocument();
    expect(screen.getByText("국내산 삼겹살 300g")).toBeInTheDocument();
  });

  it("오류(통신): 정보를 불러오지 못했어요 문구와 다시 시도 버튼을 보여준다", async () => {
    mockDetail(
      { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T00:00:00+09:00" },
      500
    );

    await renderDetail();

    expect(screen.getByText("정보를 불러오지 못했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("담기에 성공하면 하단 시트로 담았어요와 장바구니 보기를 보여준다", async () => {
    mockDetail(sampleDetail());
    server.use(
      http.post(`${BASE}/api/cart/items`, () =>
        HttpResponse.json(
          { cartItemId: 91, productId: 12, quantity: 1, currentPrice: 8400, cartItemCount: 1 },
          { status: 201 }
        )
      )
    );

    await renderDetail();
    await userEvent.click(screen.getByRole("button", { name: "장바구니 담기" }));

    expect(await screen.findByText("장바구니에 담았어요")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "장바구니 보기" })).toHaveAttribute("href", "/cart");
  });

  it("오류(품목 수 초과): 하단 시트로 안내 문구와 장바구니로 이동 버튼을 보여준다", async () => {
    mockDetail(sampleDetail());
    server.use(
      http.post(`${BASE}/api/cart/items`, () =>
        HttpResponse.json(
          {
            code: "CART_ITEM_LIMIT_EXCEEDED",
            message: "장바구니에는 품목을 10개까지 담을 수 있어요. 정리한 뒤 다시 담아주세요.",
            serverTime: "2026-08-31T00:00:00+09:00",
          },
          { status: 409 }
        )
      )
    );

    await renderDetail();
    await userEvent.click(screen.getByRole("button", { name: "장바구니 담기" }));

    expect(
      await screen.findByText(
        "장바구니에는 품목을 10개까지 담을 수 있어요. 정리한 뒤 다시 담아주세요."
      )
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "장바구니로 이동" })).toHaveAttribute(
      "href",
      "/cart"
    );
  });

  it("수량 조절기가 1회 최대 수량에 도달하면 + 버튼이 비활성되고 안내 캡션을 보여준다", async () => {
    mockDetail(sampleDetail({ maxOrderQuantity: 2 }));

    await renderDetail();
    const increaseButton = screen.getByRole("button", { name: "수량 늘리기" });
    await userEvent.click(increaseButton);

    expect(increaseButton).toBeDisabled();
    expect(screen.getByText("1회 최대 2개까지 담을 수 있어요")).toBeInTheDocument();
  });
});
