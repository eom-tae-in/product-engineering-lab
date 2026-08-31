import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/server";
import StorePage from "./page";
import StoreLoading from "./loading";

const BASE = "http://test.local";

const refreshMock = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh: refreshMock }),
}));

function mockStoreInfo(overrides: Partial<Record<string, unknown>> = {}) {
  server.use(
    http.get(`${BASE}/api/store`, () =>
      HttpResponse.json({
        name: "savePick 신선마켓",
        address: "서울특별시 ○○구 ○○로 12",
        phone: "0212345678",
        openTime: "10:00",
        closeTime: "22:00",
        slotUnitMinutes: 30,
        ...overrides,
      })
    )
  );
}

describe("SC-015 매장·픽업 안내", () => {
  it("기본 상태: 매장 정보와 픽업 절차 3단계를 보여준다", async () => {
    mockStoreInfo();

    render(await StorePage());

    expect(screen.getByText("savePick 신선마켓")).toBeInTheDocument();
    expect(screen.getByText("서울특별시 ○○구 ○○로 12")).toBeInTheDocument();
    expect(screen.getByText("0212345678")).toBeInTheDocument();
    expect(screen.getByText("10:00~22:00")).toBeInTheDocument();
    expect(screen.getByText("시간대에 방문")).toBeInTheDocument();
    expect(screen.getByText("픽업 번호 말하기")).toBeInTheDocument();
    expect(screen.getByText("수령 확인")).toBeInTheDocument();
    expect(
      screen.getByText("지도와 실시간 위치 안내는 제공하지 않아요")
    ).toBeInTheDocument();
  });

  it("빈 상태: 예정된 픽업이 없으면 안내 문구를 보여준다", async () => {
    mockStoreInfo();

    render(await StorePage());

    expect(screen.getByText("예정된 픽업이 없어요")).toBeInTheDocument();
  });

  it("로딩 상태: 카드 스켈레톤을 보여준다", () => {
    render(<StoreLoading />);

    expect(screen.getAllByRole("status", { name: "불러오는 중" }).length).toBe(3);
  });

  it("오류: 매장 정보를 불러오지 못했어요 문구와 다시 시도 버튼을 보여준다", async () => {
    server.use(
      http.get(`${BASE}/api/store`, () =>
        HttpResponse.json(
          {
            code: "INTERNAL_ERROR",
            message: "서버 오류",
            serverTime: "2026-08-31T00:00:00+09:00",
          },
          { status: 500 }
        )
      )
    );

    render(await StorePage());

    expect(screen.getByText("매장 정보를 불러오지 못했어요")).toBeInTheDocument();
    const retryButton = screen.getByRole("button", { name: "다시 시도" });
    expect(retryButton).toBeInTheDocument();
  });
});
