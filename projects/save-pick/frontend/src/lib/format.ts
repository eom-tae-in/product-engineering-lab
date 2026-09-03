import { kstDateString } from "./server-time";

/**
 * docs/11-api-spec.md §0.4: 서버가 주는 모든 시각은 ISO 8601 + KST 오프셋(+09:00)으로
 * 온다. 브라우저 타임존 설정과 무관하게 항상 같은 값을 보여주기 위해 `Date`로 파싱해
 * 재계산하지 않고, 이미 KST로 온 문자열을 그대로 잘라 쓴다.
 */
export function formatKstTime(isoString: string): string {
  const match = isoString.match(/T(\d{2}:\d{2})/);
  return match ? match[1] : isoString;
}

export function formatKstDateTime(isoString: string): string {
  const match = isoString.match(/^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/);
  return match ? `${match[1]} ${match[2]}` : isoString;
}

/**
 * docs/09-ui-design-brief.md §2.4 상품 카드·상세의 마감 시각 표기.
 *
 * 09번이 예시로 든 `오늘 21:00 마감`의 "오늘"을 날짜와 무관하게 붙이면 안 된다 —
 * 마감 시각은 "미래이고 시각(시:분)이 영업 종료 시각 이내"이기만 하면 되므로(BR-003)
 * 다음 날 마감인 상품이 실제로 존재한다. 그런 상품에 "오늘"을 붙이면 사실과 다른
 * 정보가 된다. 오늘·내일은 그대로 부르고, 그 이후는 날짜를 보여준다.
 */
export function formatKstClosingDay(isoString: string): string {
  const match = isoString.match(/^(\d{4})-(\d{2})-(\d{2})T/);
  if (!match) return "";
  const [, year, month, day] = match;
  const date = `${year}-${month}-${day}`;
  if (date === kstDateString(0)) return "오늘";
  if (date === kstDateString(1)) return "내일";
  return `${Number(month)}월 ${Number(day)}일`;
}

/** `오늘 21:00` · `내일 20:00` · `9월 5일 20:00`. */
export function formatKstClosing(isoString: string): string {
  return `${formatKstClosingDay(isoString)} ${formatKstTime(isoString)}`;
}

/**
 * docs/09-ui-design-brief.md §2.4 상품 카드 등에서 쓰는 금액 표기(`12,000원`).
 * 모든 금액은 원 단위 정수다(11번 §0.4).
 */
export function formatWon(amount: number): string {
  return `${amount.toLocaleString("ko-KR")}원`;
}

/**
 * docs/09-ui-design-brief.md §2.5 선점 타이머 바 등에서 쓰는 `mm:ss` 표기(`09:58`).
 * 음수는 0으로 취급한다 — 만료 판정은 호출부가 별도로 한다.
 */
export function formatMmSs(totalSeconds: number): string {
  const clamped = Math.max(0, Math.floor(totalSeconds));
  const minutes = Math.floor(clamped / 60);
  const seconds = clamped % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}
