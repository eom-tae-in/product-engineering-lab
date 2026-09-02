import { AdminOrderDetailView } from "@/features/admin-order/components/AdminOrderDetailView";

/**
 * SC-110 · 주문 상세 (관리자) (docs/06-screen-list.md §4).
 * 이 페이지 자체는 동적 라우트 파라미터(`id`)만 서버에서 풀어 넘긴다. 관리자 인증이
 * 필요한 조회·상태 변경은 Client Component(`AdminOrderDetailView`)가 맡는다
 * (ARCHITECTURE.md 데이터 페칭 규칙 — 액세스 토큰이 서버에 없기 때문).
 */
export default async function AdminOrderDetailPage({
  params,
}: PageProps<"/admin/orders/[id]">) {
  const { id } = await params;
  return <AdminOrderDetailView orderId={Number(id)} />;
}
