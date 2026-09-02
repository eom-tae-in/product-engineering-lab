/**
 * SC-102(관리자 홈) 전용 화면 조합 타입.
 *
 * docs/11-api-spec.md에는 관리자 홈 요약을 한 번에 내려주는 전용 엔드포인트가 없다
 * (§0.5 카탈로그와 §9·§10을 확인했지만 "대시보드"·"요약" 성격의 API가 없다). 그래서 이
 * 도메인은 새 API를 부르지 않고 이미 있는 API-112(주문 목록)·API-110(재고 현황)·
 * API-118(픽업 시간대)을 조합해 06번이 요구하는 요약 값을 만든다 — 최종 보고에도 남긴다.
 */

/** SC-102 "다음 픽업 시간대와 그 시간대 예약 건수". */
export interface NextPickupSlotSummary {
  slotId: number;
  startAt: string;
  endAt: string;
  reservedCount: number;
  capacity: number;
}

/** SC-102 "오늘 요약" 5개 값 + 다음 픽업 시간대. */
export interface AdminHomeSummary {
  confirmedCount: number;
  readyCount: number;
  completedCount: number;
  noShowCount: number;
  /** API-110 `onlyUnavailable=true` 결과 건수. "판매 가능 0인 상품 수"에 대응한다. */
  unavailableProductCount: number;
  /** 오늘 시간대 중 아직 시작하지 않은 첫 시간대. 하나도 없으면 null이다. */
  nextSlot: NextPickupSlotSummary | null;
}
