import { AdminProductEditView } from "@/features/admin-product/components/AdminProductEditView";

/**
 * SC-104 · 상품 수정 (docs/06-screen-list.md §4 "기본(수정)").
 * 이 페이지 자체는 동적 라우트 파라미터(`id`)만 서버에서 풀어 넘긴다. 관리자 인증이
 * 필요한 조회·저장은 Client Component(`AdminProductEditView`)가 맡는다
 * (ARCHITECTURE.md 데이터 페칭 규칙 — 액세스 토큰이 서버에 없기 때문).
 */
export default async function AdminProductEditPage({
  params,
}: PageProps<"/admin/products/[id]">) {
  const { id } = await params;
  return <AdminProductEditView productId={Number(id)} />;
}
