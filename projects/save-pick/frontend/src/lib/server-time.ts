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
