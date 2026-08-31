import type { MemberRole } from "@/lib/auth";

/** docs/11-api-spec.md API-001 요청. */
export interface SignupRequest {
  email: string;
  password: string;
  name: string;
  phone: string;
  guestToken?: string;
}

/** docs/11-api-spec.md API-001 응답 (201). */
export interface SignupResponse {
  memberId: number;
  email: string;
  name: string;
  role: MemberRole;
  accessToken: string;
  accessTokenExpiresAt: string;
  cartMerged: boolean;
}

export type OrderPermission = "ALLOWED" | "RESTRICTED";

/** docs/11-api-spec.md API-005 응답. */
export interface MeResponse {
  memberId: number;
  email: string;
  name: string;
  phone: string;
  orderPermission: OrderPermission;
}

/** docs/11-api-spec.md API-006 요청. 이메일은 요청에 담아도 서버가 무시한다. */
export interface UpdateMeRequest {
  name: string;
  phone: string;
}

/** docs/11-api-spec.md API-006 응답. */
export interface UpdateMeResponse {
  memberId: number;
  email: string;
  name: string;
  phone: string;
}

export interface NoShowOrderSummary {
  orderNo: string;
  noShowAt: string;
}

/** docs/11-api-spec.md API-007 응답. */
export interface NoShowStatusResponse {
  recentNoShowCount: number;
  windowDays: number;
  orderPermission: OrderPermission;
  restrictedUntil: string | null;
  noShowOrders: NoShowOrderSummary[];
}
