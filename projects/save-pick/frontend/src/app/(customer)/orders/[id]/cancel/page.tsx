import { OrderCancelView } from "@/features/order/components/OrderCancelView";

/**
 * SC-011 · 주문 취소 확인 (docs/06-screen-list.md §3).
 * 이 페이지 자체는 동적 라우트 파라미터(`id`)만 서버에서 풀어 넘긴다. 로그인이 필요한
 * 조회·취소 실행은 Client Component(`OrderCancelView`)가 맡는다(ARCHITECTURE.md
 * 데이터 페칭 규칙 — 액세스 토큰이 서버에 없기 때문).
 */
export default async function OrderCancelPage({
  params,
}: PageProps<"/orders/[id]/cancel">) {
  const { id } = await params;
  return <OrderCancelView orderId={Number(id)} />;
}
