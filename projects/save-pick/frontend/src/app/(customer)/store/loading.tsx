import { Skeleton } from "@/components/ui/Skeleton";

/** SC-015 로딩 상태: 카드 스켈레톤 (docs/06-screen-list.md §3). */
export default function StoreLoading() {
  return (
    <div className="flex flex-col gap-3 p-4">
      <Skeleton className="h-20 w-full" />
      <Skeleton className="h-32 w-full" />
      <Skeleton className="h-40 w-full" />
    </div>
  );
}
