import { serverGet } from "./api-client";

export interface ServerTimeResponse {
  serverTime: string;
  timezone: string;
}

/**
 * API-008로 서버-클라이언트 시각 차이(offset, ms)를 구한다.
 * 선점 타이머(SC-005~007)·노쇼 카운트다운(SC-112)은 이 offset으로 "지금"을 계산해야
 * 클라이언트 시계가 틀려도 서버 판정과 어긋나지 않는다 (FR-005).
 */
export async function fetchServerTimeOffsetMs(): Promise<number> {
  const clientRequestedAt = Date.now();
  const { serverTime } = await serverGet<ServerTimeResponse>("/api/system/time");
  const serverEpochMs = new Date(serverTime).getTime();
  return serverEpochMs - clientRequestedAt;
}

let cachedOffsetMs = 0;

export function setServerTimeOffsetMs(offsetMs: number): void {
  cachedOffsetMs = offsetMs;
}

export function getServerTimeOffsetMs(): number {
  return cachedOffsetMs;
}

/** offset을 반영한 "지금"(epoch ms). 화면 컴포넌트는 항상 이 함수로 현재 시각을 구한다. */
export function nowOnServer(): number {
  return Date.now() + cachedOffsetMs;
}

/**
 * offset을 반영한 "지금"의 KST 날짜 문자열(`YYYY-MM-DD`). `daysFromToday`로 이후 날짜를
 * 구한다(기본 0 = 오늘, 1 = 내일). SC-108(관리자 주문 목록)이 `pickupDate` 쿼리 파라미터를
 * 만들 때 쓴다 — 서버가 주는 시각 문자열은 항상 이미 KST라 파싱이 필요 없지만
 * (`lib/format.ts` 상단 주석), "오늘 날짜" 자체는 서버가 내려주지 않아 클라이언트가
 * 직접 계산해야 한다. 로컬 타임존과 무관하게 항상 KST 기준 날짜가 나오도록 UTC
 * 메서드로만 계산한다.
 */
export function kstDateString(daysFromToday = 0): string {
  const epochMs = nowOnServer() + daysFromToday * 24 * 60 * 60 * 1000;
  const kst = new Date(epochMs + 9 * 60 * 60 * 1000);
  const yyyy = kst.getUTCFullYear();
  const mm = String(kst.getUTCMonth() + 1).padStart(2, "0");
  const dd = String(kst.getUTCDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}
