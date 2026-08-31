import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AdminAuthProvider } from "@/lib/auth/admin-auth";
import AdminProductEditPage from "./page";

const BASE = "http://test.local";

const pushMock = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
}));

async function renderPage() {
  const page = await AdminProductEditPage({
    params: Promise.resolve({ id: "12" }),
    searchParams: Promise.resolve({}),
  });
  return render(<AdminAuthProvider>{page}</AdminAuthProvider>);
}

function mockAuthenticatedSession() {
  server.use(
    http.post(`${BASE}/api/auth/token/refresh`, () =>
      HttpResponse.json({
        accessToken: "admin-access-token",
        accessTokenExpiresAt: "2026-08-31T01:00:00+09:00",
      })
    )
  );
}

/** 클라이언트 측 마감 시각 검증(과거 여부)이 실행 시점과 무관하게 통과하도록 항상 미래 시각을 만든다. */
function futureIso(hoursAhead: number): string {
  const future = new Date(Date.now() + hoursAhead * 60 * 60 * 1000);
  const yyyy = future.getFullYear();
  const mm = String(future.getMonth() + 1).padStart(2, "0");
  const dd = String(future.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}T20:00:00+09:00`;
}

function sampleDetail(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    productId: 12,
    name: "국내산 삼겹살 300g",
    description: "오늘 손질한 국내산 삼겹살입니다.",
    saleUnit: "300g",
    originalPrice: 12000,
    closingAt: futureIso(24),
    maxOrderQuantity: 5,
    status: "ON_SALE",
    currentDiscountRate: 30,
    currentPrice: 8400,
    nextDiscountRate: 50,
    nextDiscountAt: "2026-08-31T15:00:00+09:00",
    stock: {
      totalQuantity: 20,
      availableQuantity: 12,
      heldQuantity: 2,
      confirmedQuantity: 6,
      discardedQuantity: 0,
    },
    ...overrides,
  };
}

function mockDetail(body: Record<string, unknown>, status = 200) {
  server.use(http.get(`${BASE}/api/admin/products/12`, () => HttpResponse.json(body, { status })));
}

describe("SC-104 상품 수정", () => {
  it("로딩 상태: 폼 스켈레톤을 보여준다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.get(`${BASE}/api/admin/products/12`, async () => {
        await new Promise((resolve) => setTimeout(resolve, 30));
        return HttpResponse.json(sampleDetail());
      })
    );
    await renderPage();

    expect(screen.getAllByRole("status", { name: "불러오는 중" }).length).toBeGreaterThan(0);
    await screen.findByDisplayValue("국내산 삼겹살 300g");
  });

  it("기본(수정) 상태: 기존 값과 할인 미리보기를 보여준다", async () => {
    mockAuthenticatedSession();
    mockDetail(sampleDetail());
    await renderPage();

    expect(await screen.findByDisplayValue("국내산 삼겹살 300g")).toBeInTheDocument();
    expect(screen.getByDisplayValue("300g")).toBeInTheDocument();
    expect(screen.getByDisplayValue("12000")).toBeInTheDocument();
    expect(
      screen.getByText("현재 적용 할인율 30% · 다음 구간(50%) 진입 15:00")
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "저장" })).toBeInTheDocument();
  });

  it("오류(통신): 불러오지 못했어요 문구와 다시 시도 버튼을 보여준다", async () => {
    mockAuthenticatedSession();
    mockDetail(
      { code: "INTERNAL_ERROR", message: "서버 오류", serverTime: "2026-08-31T00:00:00+09:00" },
      500
    );
    await renderPage();

    expect(await screen.findByText("불러오지 못했어요")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("오류(CLOSED 수정): 마감된 상품이면 마감 시각 입력을 비활성화하고 안내한다", async () => {
    mockAuthenticatedSession();
    mockDetail(sampleDetail({ status: "CLOSED" }));
    await renderPage();

    await screen.findByDisplayValue("국내산 삼겹살 300g");

    expect(screen.getByLabelText("마감 시각")).toBeDisabled();
    expect(screen.getByText("마감된 상품의 마감 시각은 바꿀 수 없어요")).toBeInTheDocument();
  });

  it("경고(확정 주문 영향): 확인 시트를 보여주고 동의하면 다시 저장한다", async () => {
    mockAuthenticatedSession();
    mockDetail(sampleDetail());
    let attempt = 0;
    server.use(
      http.patch(`${BASE}/api/admin/products/12`, async ({ request }) => {
        attempt += 1;
        const body = (await request.json()) as { confirmEarlierClosing?: boolean };
        if (attempt === 1 && !body.confirmEarlierClosing) {
          return HttpResponse.json(
            {
              code: "VALIDATION_ERROR",
              message: "확정 주문에 영향을 줘요",
              serverTime: "2026-08-31T00:00:00+09:00",
              details: { affectedConfirmedOrderCount: 3 },
            },
            { status: 400 }
          );
        }
        return HttpResponse.json({
          productId: 12,
          originalPrice: 12000,
          closingAt: "2026-08-31T19:00:00+09:00",
          maxOrderQuantity: 5,
          changedFields: ["closingAt"],
          affectedConfirmedOrderCount: 3,
          updatedAt: "2026-08-31T10:00:00+09:00",
        });
      })
    );
    await renderPage();

    await screen.findByDisplayValue("국내산 삼겹살 300g");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(
      await screen.findByText(
        "확정된 주문 3건의 픽업 시간대보다 빨라져요. 그래도 저장할까요?"
      )
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "그래도 저장" }));

    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/admin/products"));
  });
});
