import { StockLedgerView } from "@/features/stock/components/StockLedgerView";

/**
 * SC-106 · 재고 변경 이력 (docs/06-screen-list.md §4).
 * 이 페이지 자체는 동적 라우트 파라미터(`productId`)만 서버에서 풀어 넘긴다. 관리자
 * 인증이 필요한 조회는 Client Component(`StockLedgerView`)가 맡는다
 * (ARCHITECTURE.md 데이터 페칭 규칙 — 액세스 토큰이 서버에 없기 때문).
 */
export default async function AdminStockLedgerPage({
  params,
}: PageProps<"/admin/stocks/[productId]/ledger">) {
  const { productId } = await params;
  return <StockLedgerView productId={Number(productId)} />;
}
