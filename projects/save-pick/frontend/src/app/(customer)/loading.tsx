import { Skeleton } from "@/components/ui/Skeleton";

/** SC-001 로딩 상태: 카드 스켈레톤 4장, 정렬 바는 비활성(docs/06-screen-list.md §3). */
export default function HomeLoading() {
  return (
    <div className="flex flex-col gap-3 p-4">
      <Skeleton className="h-12 w-full" />
      <div aria-hidden className="flex items-center justify-between opacity-50">
        <span className="font-caption text-text-weak">마감 임박 순</span>
        <span className="font-caption text-text-weak">품절 숨기기</span>
      </div>
      <Skeleton className="h-[170px] w-full" />
      <Skeleton className="h-[170px] w-full" />
      <Skeleton className="h-[170px] w-full" />
      <Skeleton className="h-[170px] w-full" />
    </div>
  );
}
