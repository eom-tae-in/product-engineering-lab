import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AuthProvider } from "@/lib/auth/customer-auth";
import type { CartItem } from "@/features/cart/types";
import CartPage from "./page";

const BASE = "http://test.local";
const pushMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock, replace: vi.fn() }),
}));

function renderPage() {
  return render(
    <AuthProvider>
      <CartPage />
    </AuthProvider>
  );
}

/**
 * 비로그인이지만 장바구니에 담은 것이 있는 상태. 담기 응답에서 받은 게스트 토큰이
 * localStorage에 남아 있어야 조회가 가능하다(API-012는 인증 토큰이나 게스트 토큰 중
 * 하나를 요구한다). 토큰을 심지 않으면 "아직 아무것도 담지 않은 방문자"가 된다.
 */
function mockGuestSession() {
  window.localStorage.setItem("savepick.guestToken", "guest-token-for-test");
  server.use(
    http.post(`${BASE}/api/auth/token/refresh`, () =>
      HttpResponse.json(
        {
          code: "UNAUTHENTICATED",
          message: "인증이 필요해요.",
          serverTime: "2026-08-31T00:00:00+09:00",
        },
        { status: 401 }
      )
    )
  );
}

function mockAuthenticatedSession() {
  server.use(
    http.post(`${BASE}/api/auth/token/refresh`, () =>
      HttpResponse.json({
        accessToken: "access-token",
        accessTokenExpiresAt: "2026-08-31T01:00:00+09:00",
      })
    )
  );
}

function sampleItem(overrides: Partial<CartItem> = {}): CartItem {
  return {
    cartItemId: 1,
    productId: 12,
    name: "국내산 삼겹살 300g",
    quantity: 2,
    addedPrice: 8400,
    currentPrice: 8400,
    priceChanged: false,
    lineAmount: 16800,
    availableQuantity: 10,
    shortage: 0,
    purchasable: true,
    unavailableReason: null,
    ...overrides,
  };
}

function computeTotal(items: CartItem[]) {
  return items.reduce((sum, item) => sum + item.lineAmount, 0);
}

function computeOrderable(items: CartItem[]) {
  return items.length > 0 && items.every((item) => item.purchasable);
}

/** GET/PATCH/DELETE가 같은 상태를 공유하는 살아있는 장바구니 목킹. */
function installCart(initialItems: CartItem[]) {
  const state = { items: initialItems.map((item) => ({ ...item })) };

  server.use(
    http.get(`${BASE}/api/cart`, () =>
      HttpResponse.json({
        serverTime: "2026-08-31T10:00:00+09:00",
        guestToken: "guest-token-1",
        items: state.items,
        totalAmount: computeTotal(state.items),
        orderable: computeOrderable(state.items),
      })
    ),
    http.patch(`${BASE}/api/cart/items/:cartItemId`, async ({ params, request }) => {
      const cartItemId = Number(params.cartItemId);
      const body = (await request.json()) as { quantity: number };
      if (body.quantity <= 0) {
        state.items = state.items.filter((item) => item.cartItemId !== cartItemId);
        return new HttpResponse(null, { status: 204 });
      }
      const item = state.items.find((entry) => entry.cartItemId === cartItemId);
      if (!item) {
        return HttpResponse.json(
          { code: "NOT_FOUND", message: "없음", serverTime: "2026-08-31T10:00:00+09:00" },
          { status: 404 }
        );
      }
      item.quantity = body.quantity;
      item.lineAmount = item.currentPrice * body.quantity;
      return HttpResponse.json({
        cartItemId,
        quantity: item.quantity,
        lineAmount: item.lineAmount,
        totalAmount: computeTotal(state.items),
      });
    }),
    http.delete(`${BASE}/api/cart/items/unavailable`, () => {
      const removedCartItemIds = state.items
        .filter((item) => !item.purchasable)
        .map((item) => item.cartItemId);
      state.items = state.items.filter((item) => item.purchasable);
      return HttpResponse.json({
        removedCartItemIds,
        remainingItemCount: state.items.length,
        totalAmount: computeTotal(state.items),
        orderable: computeOrderable(state.items),
      });
    }),
    http.delete(`${BASE}/api/cart/items/:cartItemId`, ({ params }) => {
      const cartItemId = Number(params.cartItemId);
      state.items = state.items.filter((item) => item.cartItemId !== cartItemId);
      return new HttpResponse(null, { status: 204 });
    })
  );

  return state;
}

describe("SC-004 장바구니", () => {
  it("기본 상태: 품목 목록과 금액 요약, 주문하기 버튼을 보여준다", async () => {
    mockGuestSession();
    installCart([
      sampleItem({ cartItemId: 1, name: "국내산 삼겹살 300g", quantity: 2, lineAmount: 16800 }),
      sampleItem({
        cartItemId: 2,
        productId: 30,
        name: "대파 1단",
        quantity: 1,
        addedPrice: 2100,
        currentPrice: 2100,
        lineAmount: 2100,
      }),
    ]);
    renderPage();

    expect(await screen.findByText("국내산 삼겹살 300g")).toBeInTheDocument();
    expect(screen.getByText("대파 1단")).toBeInTheDocument();
    expect(screen.getByText("16,800원")).toBeInTheDocument();
    expect(screen.getAllByText("18,900원").length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "주문하기" })).toBeEnabled();
  });

  it("로딩 상태: 품목 행 스켈레톤을 보여주고 주문하기는 비활성이다", async () => {
    mockGuestSession();
    server.use(
      http.get(`${BASE}/api/cart`, async () => {
        await new Promise((resolve) => setTimeout(resolve, 30));
        return HttpResponse.json({
          serverTime: "2026-08-31T10:00:00+09:00",
          guestToken: "guest-token-1",
          items: [],
          totalAmount: 0,
          orderable: false,
        });
      })
    );
    renderPage();

    expect(screen.getAllByRole("status", { name: "불러오는 중" }).length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "주문하기" })).toBeDisabled();
    await screen.findByText("장바구니가 비어 있어요");
  });

  it("빈 상태: 장바구니가 비어 있어요 문구와 이동 버튼을 보여준다", async () => {
    mockGuestSession();
    installCart([]);
    renderPage();

    expect(await screen.findByText("장바구니가 비어 있어요")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "마감 할인 상품 보러 가기" })).toHaveAttribute(
      "href",
      "/"
    );
  });

  it("오류(통신): 장바구니를 불러오지 못했어요와 다시 시도를 보여주고 재시도하면 복구된다", async () => {
    mockGuestSession();
    let attempt = 0;
    server.use(
      http.get(`${BASE}/api/cart`, () => {
        attempt += 1;
        if (attempt === 1) {
          return HttpResponse.json(
            { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T10:00:00+09:00" },
            { status: 500 }
          );
        }
        return HttpResponse.json({
          serverTime: "2026-08-31T10:00:00+09:00",
          guestToken: "guest-token-1",
          items: [sampleItem()],
          totalAmount: 16800,
          orderable: true,
        });
      })
    );
    renderPage();

    expect(await screen.findByText("장바구니를 불러오지 못했어요")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(await screen.findByText("국내산 삼겹살 300g")).toBeInTheDocument();
  });

  it("재고 소진: 품절 배지와 조절기 비활성, 상단 안내와 일괄 삭제 버튼을 보여준다", async () => {
    mockGuestSession();
    installCart([
      sampleItem({ cartItemId: 1 }),
      sampleItem({
        cartItemId: 3,
        productId: 40,
        name: "훈제 오리 400g",
        quantity: 1,
        addedPrice: 7500,
        currentPrice: 7500,
        lineAmount: 7500,
        availableQuantity: 0,
        shortage: 1,
        purchasable: false,
        unavailableReason: "OUT_OF_STOCK",
      }),
    ]);
    renderPage();

    expect(await screen.findByText("구매할 수 없는 품목 1건이 있어요")).toBeInTheDocument();
    expect(screen.getByText("품절")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "훈제 오리 400g 수량 줄이기" })
    ).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "훈제 오리 400g 수량 늘리기" })
    ).toBeDisabled();
    expect(screen.getByRole("button", { name: "구매 불가 품목 삭제" })).toBeInTheDocument();
  });

  it("재고 부족: 남은 수량·부족 수량 캡션을 보여주고 줄이기만 가능하다", async () => {
    mockGuestSession();
    installCart([
      sampleItem({
        cartItemId: 4,
        productId: 50,
        name: "방울토마토 500g",
        quantity: 3,
        addedPrice: 3000,
        currentPrice: 3000,
        lineAmount: 9000,
        availableQuantity: 1,
        shortage: 2,
        purchasable: false,
        unavailableReason: "OUT_OF_STOCK",
      }),
    ]);
    renderPage();

    expect(await screen.findByText("남은 수량 1개 · 2개 부족")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "방울토마토 500g 수량 줄이기" })
    ).toBeEnabled();
    expect(
      screen.getByRole("button", { name: "방울토마토 500g 수량 늘리기" })
    ).toBeDisabled();
  });

  it("가격 변동: 변경 전·후 가격 캡션을 보여준다", async () => {
    mockGuestSession();
    installCart([
      sampleItem({
        cartItemId: 2,
        productId: 30,
        name: "대파 1단",
        quantity: 1,
        addedPrice: 8400,
        currentPrice: 6000,
        priceChanged: true,
        lineAmount: 6000,
      }),
    ]);
    renderPage();

    expect(
      await screen.findByText("할인 구간이 바뀌어 가격이 8,400원 → 6,000원으로 변경됐어요")
    ).toBeInTheDocument();
  });

  it("마감 도달: 판매 종료 배지를 보여주고 구매 불가 품목으로 분류한다", async () => {
    mockGuestSession();
    installCart([
      sampleItem({ cartItemId: 1 }),
      sampleItem({
        cartItemId: 5,
        productId: 60,
        name: "감자 1kg",
        quantity: 1,
        addedPrice: 4000,
        currentPrice: 4000,
        lineAmount: 4000,
        purchasable: false,
        unavailableReason: "PRODUCT_CLOSED",
      }),
    ]);
    renderPage();

    expect(await screen.findByText("판매 종료")).toBeInTheDocument();
    expect(screen.getByText("구매할 수 없는 품목 1건이 있어요")).toBeInTheDocument();
  });

  it("품목 10개 도달: 정리 안내 배너를 보여준다", async () => {
    mockGuestSession();
    installCart(
      Array.from({ length: 10 }, (_, index) =>
        sampleItem({
          cartItemId: index + 1,
          productId: index + 1,
          name: `상품 ${index + 1}`,
        })
      )
    );
    renderPage();

    expect(
      await screen.findByText(
        "장바구니에 품목을 10개까지 담았어요. 새 품목을 담으려면 먼저 정리해주세요"
      )
    ).toBeInTheDocument();
  });

  it("구매 불가 존재: 주문하기가 비활성되고 안내 문구를 보여준다", async () => {
    mockGuestSession();
    installCart([
      sampleItem({ cartItemId: 1 }),
      sampleItem({
        cartItemId: 3,
        productId: 40,
        name: "훈제 오리 400g",
        quantity: 1,
        availableQuantity: 0,
        shortage: 1,
        purchasable: false,
        unavailableReason: "OUT_OF_STOCK",
      }),
    ]);
    renderPage();

    await screen.findByText("국내산 삼겹살 300g");
    expect(
      screen.getByText("구매할 수 없는 품목을 정리하면 주문할 수 있어요")
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "주문하기" })).toBeDisabled();
  });

  it("수량 변경: +를 누르면 수량과 합계가 갱신된다", async () => {
    mockGuestSession();
    installCart([sampleItem({ cartItemId: 1, quantity: 2, currentPrice: 8400, lineAmount: 16800 })]);
    renderPage();

    await screen.findByText("국내산 삼겹살 300g");
    await userEvent.click(
      screen.getByRole("button", { name: "국내산 삼겹살 300g 수량 늘리기" })
    );

    await waitFor(() => expect(screen.getAllByText("25,200원").length).toBeGreaterThan(0));
  });

  it("수량 변경: 0으로 내리면 품목이 삭제된다", async () => {
    mockGuestSession();
    installCart([sampleItem({ cartItemId: 1, quantity: 1 })]);
    renderPage();

    await screen.findByText("국내산 삼겹살 300g");
    await userEvent.click(
      screen.getByRole("button", { name: "국내산 삼겹살 300g 수량 줄이기" })
    );

    expect(await screen.findByText("장바구니가 비어 있어요")).toBeInTheDocument();
  });

  it("품목 삭제: 삭제 버튼을 누르면 목록에서 빠진다", async () => {
    mockGuestSession();
    installCart([sampleItem({ cartItemId: 1 })]);
    renderPage();

    await screen.findByText("국내산 삼겹살 300g");
    await userEvent.click(screen.getByRole("button", { name: "삭제" }));

    expect(await screen.findByText("장바구니가 비어 있어요")).toBeInTheDocument();
  });

  it("구매 불가 품목 일괄 삭제: 성공하면 안내가 사라지고 주문하기가 활성화된다", async () => {
    mockGuestSession();
    installCart([
      sampleItem({ cartItemId: 1 }),
      sampleItem({
        cartItemId: 3,
        productId: 40,
        name: "훈제 오리 400g",
        quantity: 1,
        availableQuantity: 0,
        shortage: 1,
        purchasable: false,
        unavailableReason: "OUT_OF_STOCK",
      }),
    ]);
    renderPage();

    await screen.findByText("구매할 수 없는 품목 1건이 있어요");
    await userEvent.click(screen.getByRole("button", { name: "구매 불가 품목 삭제" }));

    await waitFor(() =>
      expect(screen.queryByText("구매할 수 없는 품목 1건이 있어요")).not.toBeInTheDocument()
    );
    expect(screen.getByRole("button", { name: "주문하기" })).toBeEnabled();
  });

  it("주문하기: 비로그인 상태면 로그인 화면으로 보낸다", async () => {
    mockGuestSession();
    installCart([sampleItem({ cartItemId: 1 })]);
    renderPage();

    await screen.findByText("국내산 삼겹살 300g");
    await userEvent.click(screen.getByRole("button", { name: "주문하기" }));

    expect(pushMock).toHaveBeenCalledWith("/login");
  });

  it("주문하기: 성공하면 생성된 주문의 상세로 이동한다", async () => {
    mockAuthenticatedSession();
    installCart([sampleItem({ cartItemId: 1 })]);
    server.use(
      http.post(`${BASE}/api/orders`, () =>
        HttpResponse.json(
          {
            orderId: 555,
            orderNo: "ORD-20260831-000001",
            status: "PENDING",
            serverTime: "2026-08-31T10:00:00+09:00",
            holdExpiresAt: "2026-08-31T10:10:00+09:00",
            holdRemainingSeconds: 600,
            paymentAttemptRemaining: 3,
            totalAmount: 16800,
            items: [],
            earliestClosingAt: "2026-08-31T21:00:00+09:00",
          },
          { status: 201 }
        )
      )
    );
    renderPage();

    await screen.findByText("국내산 삼겹살 300g");
    await userEvent.click(screen.getByRole("button", { name: "주문하기" }));

    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/orders/new?orderId=555"));
  });

  it("오류(주문서 생성 실패 · 품절): 부족 품목과 새로고침 버튼을 하단 시트로 보여준다", async () => {
    mockAuthenticatedSession();
    installCart([sampleItem({ cartItemId: 1, productId: 12, name: "국내산 삼겹살 300g" })]);
    server.use(
      http.post(`${BASE}/api/orders`, () =>
        HttpResponse.json(
          {
            code: "OUT_OF_STOCK",
            message: "재고가 부족해요.",
            serverTime: "2026-08-31T10:00:00+09:00",
            details: { shortages: [{ productId: 12, requested: 2, available: 1 }] },
          },
          { status: 409 }
        )
      )
    );
    renderPage();

    await screen.findByText("국내산 삼겹살 300g");
    await userEvent.click(screen.getByRole("button", { name: "주문하기" }));

    expect(await screen.findByText("방금 다른 고객이 먼저 담았어요")).toBeInTheDocument();
    expect(
      screen.getByText("국내산 삼겹살 300g — 요청 2개 / 남은 수량 1개")
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "장바구니 새로고침" })).toBeInTheDocument();
  });

  // TC-110·TC-121(X5): 13번 §3의 lock_timeout 초과가 INTERNAL_ERROR(500)로 올라올 때
  // SC-004가 오류 상태로 노출하는지 확인한다.
  it("TC-110 오류(주문서 생성 실패 · 시스템 오류): 안내 문구와 다시 시도를 하단 시트로 보여준다", async () => {
    mockAuthenticatedSession();
    installCart([sampleItem({ cartItemId: 1 })]);
    server.use(
      http.post(`${BASE}/api/orders`, () =>
        HttpResponse.json(
          { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T10:00:00+09:00" },
          { status: 500 }
        )
      )
    );
    renderPage();

    await screen.findByText("국내산 삼겹살 300g");
    await userEvent.click(screen.getByRole("button", { name: "주문하기" }));

    expect(
      await screen.findByText(
        "일시적인 오류로 주문서를 만들지 못했어요. 잠시 뒤 다시 시도해주세요"
      )
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("오류(주문 제한): 노쇼 해제 예정 시각을 하단 시트로 보여준다", async () => {
    mockAuthenticatedSession();
    installCart([sampleItem({ cartItemId: 1 })]);
    server.use(
      http.post(`${BASE}/api/orders`, () =>
        HttpResponse.json(
          {
            code: "ORDER_RESTRICTED",
            message: "주문이 제한됐어요.",
            serverTime: "2026-08-31T10:00:00+09:00",
            details: { restrictedUntil: "2026-09-04T19:00:00+09:00" },
          },
          { status: 403 }
        )
      ),
      http.get(`${BASE}/api/me/no-show-status`, () =>
        HttpResponse.json({
          recentNoShowCount: 3,
          windowDays: 30,
          orderPermission: "RESTRICTED",
          restrictedUntil: "2026-09-04T19:00:00+09:00",
          noShowOrders: [],
        })
      )
    );
    renderPage();

    await screen.findByText("국내산 삼겹살 300g");
    await userEvent.click(screen.getByRole("button", { name: "주문하기" }));

    expect(
      await screen.findByText("노쇼 3회 누적으로 2026-09-04 19:00까지 주문할 수 없어요")
    ).toBeInTheDocument();
  });

  it("오류(진행 중 주문서 존재): 주문서로 이동 버튼을 하단 시트로 보여준다", async () => {
    mockAuthenticatedSession();
    installCart([sampleItem({ cartItemId: 1 })]);
    server.use(
      http.post(`${BASE}/api/orders`, () =>
        HttpResponse.json(
          {
            code: "PENDING_ORDER_EXISTS",
            message: "진행 중인 주문서가 있어요.",
            serverTime: "2026-08-31T10:00:00+09:00",
            details: { orderId: 700, holdExpiresAt: "2026-08-31T10:10:00+09:00" },
          },
          { status: 409 }
        )
      )
    );
    renderPage();

    await screen.findByText("국내산 삼겹살 300g");
    await userEvent.click(screen.getByRole("button", { name: "주문하기" }));

    expect(await screen.findByText("진행 중인 주문서가 있어요")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "주문서로 이동" }));

    expect(pushMock).toHaveBeenCalledWith("/orders/new?orderId=700");
  });

  it("아직 아무것도 담지 않은 비로그인 방문자는 요청 없이 빈 상태를 본다", async () => {
    // 게스트 토큰이 없으면 장바구니 자체가 없다. 그대로 조회하면 서버가
    // VALIDATION_ERROR(400)로 거절해 오류 화면이 뜬다(실제 브라우저에서 발견).
    window.localStorage.removeItem("savepick.guestToken");
    server.use(
      http.post(`${BASE}/api/auth/token/refresh`, () =>
        HttpResponse.json(
          { code: "UNAUTHENTICATED", message: "인증이 필요해요.", serverTime: "2026-08-31T00:00:00+09:00" },
          { status: 401 }
        )
      )
    );
    let cartCalled = false;
    server.use(
      http.get(`${BASE}/api/cart`, () => {
        cartCalled = true;
        return HttpResponse.json(
          { code: "VALIDATION_ERROR", message: "게스트 토큰 또는 인증 토큰이 필요합니다.", serverTime: "2026-08-31T10:00:00+09:00" },
          { status: 400 }
        );
      })
    );
    renderPage();

    expect(await screen.findByText("장바구니가 비어 있어요")).toBeInTheDocument();
    expect(cartCalled).toBe(false);
  });

  it("로그인 상태에서는 인증 확인이 끝난 뒤에 조회한다", async () => {
    // 확인 전(checking)에 조회하면 액세스 토큰이 실리지 않아 400으로 떨어진다.
    window.localStorage.removeItem("savepick.guestToken");
    mockAuthenticatedSession();
    let authHeader: string | null = "미호출";
    server.use(
      http.get(`${BASE}/api/cart`, ({ request }) => {
        authHeader = request.headers.get("Authorization");
        return HttpResponse.json({
          serverTime: "2026-08-31T10:00:00+09:00",
          guestToken: null,
          items: [sampleItem()],
          totalAmount: 16800,
          orderable: true,
        });
      })
    );
    renderPage();

    expect(await screen.findByText("국내산 삼겹살 300g")).toBeInTheDocument();
    expect(authHeader).toBe("Bearer access-token");
  });
});
