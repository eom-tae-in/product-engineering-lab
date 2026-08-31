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
