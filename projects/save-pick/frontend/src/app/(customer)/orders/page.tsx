import { OrderListView } from "@/features/order/components/OrderListView";

/**
 * SC-009 · 주문 내역 (docs/06-screen-list.md §3).
 * 로그인 필수 화면이라 조회는 Client Component(`OrderListView`)가 맡는다
 * (ARCHITECTURE.md 데이터 페칭 규칙 — 액세스 토큰이 서버에 없기 때문).
 */
export default function OrdersPage() {
  return <OrderListView />;
}
