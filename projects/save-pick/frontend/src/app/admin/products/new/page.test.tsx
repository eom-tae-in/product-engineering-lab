import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import { AdminAuthProvider } from "@/lib/auth/admin-auth";
import AdminProductNewPage from "./page";

const BASE = "http://test.local";

const pushMock = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
}));

function renderPage() {
  return render(
    <AdminAuthProvider>
      <AdminProductNewPage />
    </AdminAuthProvider>
  );
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

/** hoursAhead만큼 뒤의 날짜에 timeOfDay를 붙인 datetime-local 값을 만든다. */
function futureDateTimeLocal(hoursAhead: number, timeOfDay = "20:00"): string {
  const future = new Date(Date.now() + hoursAhead * 60 * 60 * 1000);
  const yyyy = future.getFullYear();
  const mm = String(future.getMonth() + 1).padStart(2, "0");
  const dd = String(future.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}T${timeOfDay}`;
}

async function fillValidForm() {
  await userEvent.type(screen.getByLabelText("상품명"), "국내산 삼겹살 300g");
  await userEvent.type(screen.getByLabelText("판매 단위"), "300g");
  await userEvent.type(screen.getByLabelText("정가"), "12000");
  fireEvent.change(screen.getByLabelText("마감 시각"), {
    target: { value: futureDateTimeLocal(24) },
  });
}

describe("SC-104 상품 등록", () => {
  it("기본(등록) 상태: 빈 폼과 DRAFT 안내 문구를 보여준다", async () => {
    mockAuthenticatedSession();
    renderPage();

    expect(await screen.findByRole("heading", { name: "상품 등록" })).toBeInTheDocument();
    expect(
      screen.getByText("등록하면 DRAFT 상태가 되고 고객에게 보이지 않아요")
    ).toBeInTheDocument();
    expect(screen.getByLabelText("상품명")).toHaveValue("");
    expect(screen.getByRole("button", { name: "등록" })).toBeInTheDocument();
  });

  it("오류(필수 누락): 비어 있는 항목마다 안내를 보여주고 등록하지 않는다", async () => {
    mockAuthenticatedSession();
    let called = false;
    server.use(
      http.post(`${BASE}/api/admin/products`, () => {
        called = true;
        return HttpResponse.json({});
      })
    );
    renderPage();

    await userEvent.click(await screen.findByRole("button", { name: "등록" }));

    expect(await screen.findByText("상품명을 입력해주세요")).toBeInTheDocument();
    expect(screen.getByText("판매 단위를 입력해주세요")).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it("오류(마감 시각 과거): 과거 시각을 입력하면 등록하지 않는다", async () => {
    mockAuthenticatedSession();
    let called = false;
    server.use(
      http.post(`${BASE}/api/admin/products`, () => {
        called = true;
        return HttpResponse.json({});
      })
    );
    renderPage();

    await userEvent.type(await screen.findByLabelText("상품명"), "국내산 삼겹살 300g");
    await userEvent.type(screen.getByLabelText("판매 단위"), "300g");
    await userEvent.type(screen.getByLabelText("정가"), "12000");
    fireEvent.change(screen.getByLabelText("마감 시각"), {
      target: { value: "2020-01-01T20:00" },
    });
    await userEvent.click(screen.getByRole("button", { name: "등록" }));

    expect(
      await screen.findByText("마감 시각은 현재 시각 이후로 정해주세요")
    ).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it("오류(마감 시각 상한): 영업 종료 시각을 넘으면 등록하지 않는다", async () => {
    mockAuthenticatedSession();
    let called = false;
    server.use(
      http.post(`${BASE}/api/admin/products`, () => {
        called = true;
        return HttpResponse.json({});
      })
    );
    renderPage();

    await userEvent.type(await screen.findByLabelText("상품명"), "국내산 삼겹살 300g");
    await userEvent.type(screen.getByLabelText("판매 단위"), "300g");
    await userEvent.type(screen.getByLabelText("정가"), "12000");
    fireEvent.change(screen.getByLabelText("마감 시각"), {
      target: { value: futureDateTimeLocal(24, "23:00") },
    });
    await userEvent.click(screen.getByRole("button", { name: "등록" }));

    expect(
      await screen.findByText("마감 시각은 영업 종료 시각 22:00을 넘을 수 없어요")
    ).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it("오류(정가 하한): 정가가 100원 미만이면 등록하지 않는다", async () => {
    mockAuthenticatedSession();
    let called = false;
    server.use(
      http.post(`${BASE}/api/admin/products`, () => {
        called = true;
        return HttpResponse.json({});
      })
    );
    renderPage();

    await userEvent.type(await screen.findByLabelText("상품명"), "국내산 삼겹살 300g");
    await userEvent.type(screen.getByLabelText("판매 단위"), "300g");
    await userEvent.type(screen.getByLabelText("정가"), "50");
    fireEvent.change(screen.getByLabelText("마감 시각"), {
      target: { value: futureDateTimeLocal(24) },
    });
    await userEvent.click(screen.getByRole("button", { name: "등록" }));

    expect(await screen.findByText("정가는 100원 이상이어야 해요")).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it("오류(최대 수량): 1회 주문 최대 수량이 1 미만이면 등록하지 않는다", async () => {
    mockAuthenticatedSession();
    let called = false;
    server.use(
      http.post(`${BASE}/api/admin/products`, () => {
        called = true;
        return HttpResponse.json({});
      })
    );
    renderPage();

    await fillValidForm();
    fireEvent.change(screen.getByLabelText("1회 주문 최대 수량"), { target: { value: "0" } });
    await userEvent.click(screen.getByRole("button", { name: "등록" }));

    expect(
      await screen.findByText("1회 주문 최대 수량은 1 이상이어야 해요")
    ).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it("값을 모두 채우면 등록에 성공하고 수정 화면으로 이동한다", async () => {
    mockAuthenticatedSession();
    server.use(
      http.post(`${BASE}/api/admin/products`, () =>
        HttpResponse.json(
          {
            productId: 42,
            status: "DRAFT",
            name: "국내산 삼겹살 300g",
            originalPrice: 12000,
            closingAt: "2026-09-01T20:00:00+09:00",
            maxOrderQuantity: 5,
          },
          { status: 201 }
        )
      )
    );
    renderPage();

    await fillValidForm();
    await userEvent.click(screen.getByRole("button", { name: "등록" }));

    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/admin/products/42"));
  });
});
