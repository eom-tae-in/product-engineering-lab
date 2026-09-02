import { OrderDetailView } from "@/features/order/components/OrderDetailView";

/**
 * SC-010 · 주문 상세 (docs/06-screen-list.md §3), `?justConfirmed=1`이고 상태가
 * CONFIRMED일 때는 SC-008 · 주문 완료 레이아웃으로 렌더링한다(`OrderDetailView` 내부
 * 분기, 작업 지시서 — 같은 주문을 시점만 다르게 보여주는 같은 데이터라 한 페이지에서
 * 조건 분기하는 게 자연스럽다).
 *
 * 이 페이지 자체는 동적 라우트 파라미터(`id`)와 `justConfirmed` 쿼리만 서버에서 풀어
 * 넘긴다. 로그인이 필요한 조회는 Client Component(`OrderDetailView`)가 맡는다
 * (ARCHITECTURE.md 데이터 페칭 규칙 — 액세스 토큰이 서버에 없기 때문).
 */
export default async function OrderDetailPage({
  params,
  searchParams,
}: PageProps<"/orders/[id]">) {
  const { id } = await params;
  const { justConfirmed } = await searchParams;
  return <OrderDetailView orderId={Number(id)} justConfirmed={justConfirmed === "1"} />;
}
