import { Skeleton } from "@/components/ui/Skeleton";

/** SC-002 로딩 상태: 카드 스켈레톤 3장(docs/06-screen-list.md §3). */
export default function SearchLoading() {
  return (
    <div className="flex flex-col gap-3 p-4">
      <Skeleton className="h-[52px] w-full" />
      <Skeleton className="h-[170px] w-full" />
      <Skeleton className="h-[170px] w-full" />
      <Skeleton className="h-[170px] w-full" />
    </div>
  );
}
