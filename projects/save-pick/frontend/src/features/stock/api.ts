import { clientRequest } from "@/lib/api-client";
import type {
  AdjustStockRequest,
  AdjustStockResponse,
  StockLedgerListParams,
  StockLedgerListResponse,
  StockListParams,
  StockListResponse,
} from "./types";

function buildQuery(params: object): string {
  const query = new URLSearchParams();
  Object.entries(params as Record<string, string | number | boolean | undefined>).forEach(
    ([key, value]) => {
      if (value !== undefined) query.set(key, String(value));
    }
  );
  const qs = query.toString();
  return qs ? `?${qs}` : "";
}

/** API-110 재고 현황 조회. 관리자 전용(SC-105). */
export function fetchAdminStocks(params: StockListParams = {}): Promise<StockListResponse> {
  return clientRequest<StockListResponse>(`/api/admin/stocks${buildQuery(params)}`, {
    authScope: "admin",
  });
}

/** API-109 재고 등록·조정. 관리자 전용(SC-105 조정 시트). */
export function adjustStock(
  productId: number | string,
  request: AdjustStockRequest
): Promise<AdjustStockResponse> {
  return clientRequest<AdjustStockResponse>(`/api/admin/products/${productId}/stock`, {
    method: "PUT",
    authScope: "admin",
    body: request,
  });
}

/** API-111 재고 변경 이력 조회. 관리자 전용(SC-106). */
export function fetchStockLedger(
  productId: number | string,
  params: StockLedgerListParams = {}
): Promise<StockLedgerListResponse> {
  return clientRequest<StockLedgerListResponse>(
    `/api/admin/stocks/${productId}/ledger${buildQuery(params)}`,
    { authScope: "admin" }
  );
}
