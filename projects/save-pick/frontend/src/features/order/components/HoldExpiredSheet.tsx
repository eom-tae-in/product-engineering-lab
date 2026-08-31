"use client";

import { useRouter } from "next/navigation";
import { BottomSheet } from "@/components/ui/BottomSheet";
import { Button } from "@/components/ui/Button";

/**
 * SC-005·SC-006·SC-007이 공유하는 "선점 만료" 시트(docs/06-screen-list.md).
 * 딤을 탭해도 닫히지 않는다(`dismissible=false`) — 만료 사실을 반드시 인지시킨다
 * (docs/09-ui-design-brief.md §2.7). 뒤로가기로 주문서에 되돌아갈 수 없어 장바구니로만
 * 보낸다.
 */
export function HoldExpiredSheet({ open }: { open: boolean }) {
  const router = useRouter();

  return (
    <BottomSheet open={open} dismissible={false}>
      <div className="flex flex-col gap-3">
        <h3 className="font-heading">선점 시간이 끝났어요</h3>
        <p className="font-body text-text-weak">
          선점한 수량은 다른 고객이 살 수 있게 돌아갔어요
        </p>
        <Button onClick={() => router.replace("/cart")}>장바구니에서 다시 주문하기</Button>
      </div>
    </BottomSheet>
  );
}
