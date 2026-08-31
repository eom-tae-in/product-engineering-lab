import { fetchStoreInfo } from "@/features/store/api";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorState } from "@/components/ui/ErrorState";
import { RefreshButton } from "@/components/ui/RefreshButton";

/** docs/06-screen-list.md §3 SC-015가 못박은 픽업 절차 3단계 문구 그대로. */
const PICKUP_STEPS = ["시간대에 방문", "픽업 번호 말하기", "수령 확인"];

/**
 * SC-015 · 매장·픽업 안내 (docs/06-screen-list.md §3).
 * 비로그인도 볼 수 있는 조회 전용 화면이라 Server Component에서 `serverGet`으로
 * 직접 fetch한다(ARCHITECTURE.md "데이터 페칭 규칙"). 로그인 여부에 따른 분기가
 * 없어 `useAuth()`도 쓰지 않는다.
 *
 * 로딩 상태는 같은 라우트의 `loading.tsx`가 맡는다(Next.js App Router가 이 세그먼트를
 * 그리는 동안 자동으로 보여준다).
 *
 * 판단: "확정 주문이 있으면 픽업 시간대와 노쇼 전환 예정 시각을 보여준다"(06 표시 정보)는
 * 이번 슬라이스에서 구현하지 않는다. 주문 조회 API(SC-010과 공유하는 도메인)가 아직
 * 이 슬라이스 범위가 아니기 때문이다. 대신 06의 빈 상태(`예정된 픽업이 없어요`)를
 * 로그인 여부와 무관하게 항상 렌더한다. 다음 슬라이스(주문 도메인)가 이 영역을
 * 로그인 상태의 확정 주문 조회로 교체해야 한다.
 */
export default async function StorePage() {
  let store;
  try {
    store = await fetchStoreInfo();
  } catch {
    return (
      <div className="p-4">
        <ErrorState message="매장 정보를 불러오지 못했어요" action={<RefreshButton />} />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3 p-4">
      <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
        <h2 className="font-heading mb-3">예정된 픽업</h2>
        <EmptyState message="예정된 픽업이 없어요" />
      </div>

      <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
        <h2 className="font-heading mb-3">{store.name}</h2>
        <dl className="flex flex-col gap-2">
          <div className="flex justify-between border-b border-border pb-2">
            <dt className="font-caption text-text-weak">주소</dt>
            <dd className="font-body">{store.address}</dd>
          </div>
          <div className="flex justify-between border-b border-border pb-2">
            <dt className="font-caption text-text-weak">연락처</dt>
            <dd className="font-body tabular-nums">{store.phone}</dd>
          </div>
          <div className="flex justify-between pb-2">
            <dt className="font-caption text-text-weak">영업시간</dt>
            <dd className="font-body tabular-nums">
              {store.openTime}~{store.closeTime}
            </dd>
          </div>
        </dl>
        <p className="font-caption mt-2 text-text-weak">
          지도와 실시간 위치 안내는 제공하지 않아요
        </p>
      </div>

      <div className="rounded-lg border border-border bg-surface p-4 shadow-[var(--shadow-card)]">
        <h2 className="font-heading mb-3">픽업 절차</h2>
        <ol className="flex flex-col gap-3">
          {PICKUP_STEPS.map((step, index) => (
            <li key={step} className="flex items-center gap-3">
              <span className="font-heading flex h-7 w-7 flex-none items-center justify-center rounded-sm bg-brand-weak text-brand">
                {index + 1}
              </span>
              <p className="font-body">{step}</p>
            </li>
          ))}
        </ol>
      </div>
    </div>
  );
}
