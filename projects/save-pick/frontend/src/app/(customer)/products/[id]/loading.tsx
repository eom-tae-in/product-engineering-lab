import { Skeleton } from "@/components/ui/Skeleton";

/** SC-003 로딩 상태: 이미지·텍스트 스켈레톤, 하단 담기 바는 비활성(docs/06-screen-list.md §3). */
export default function ProductDetailLoading() {
  return (
    <div className="flex flex-col gap-3 p-4">
      <Skeleton className="h-40 w-full" />
      <Skeleton className="h-6 w-40" />
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-8 w-32" />
      <Skeleton className="h-4 w-24" />
    </div>
  );
}
