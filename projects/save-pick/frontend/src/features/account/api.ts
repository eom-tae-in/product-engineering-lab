import { clientRequest } from "@/lib/api-client";
import type {
  MeResponse,
  NoShowStatusResponse,
  SignupRequest,
  SignupResponse,
  UpdateMeRequest,
  UpdateMeResponse,
} from "./types";

/** API-001 회원가입. 성공하면 곧바로 인증 상태가 된다(별도 로그인 없이). */
export function signup(request: SignupRequest): Promise<SignupResponse> {
  return clientRequest<SignupResponse>("/api/auth/signup", {
    method: "POST",
    body: request,
  });
}

/** API-005 내 정보 조회. */
export function fetchMe(): Promise<MeResponse> {
  return clientRequest<MeResponse>("/api/me", { authScope: "customer" });
}

/** API-006 내 정보 수정. 이메일은 이 요청에 포함하지 않는다(서버가 어차피 무시한다). */
export function updateMe(request: UpdateMeRequest): Promise<UpdateMeResponse> {
  return clientRequest<UpdateMeResponse>("/api/me", {
    method: "PATCH",
    authScope: "customer",
    body: request,
  });
}

/** API-007 노쇼·주문 제한 상태 조회. */
export function fetchNoShowStatus(): Promise<NoShowStatusResponse> {
  return clientRequest<NoShowStatusResponse>("/api/me/no-show-status", {
    authScope: "customer",
  });
}
