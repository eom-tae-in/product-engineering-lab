import { ApiError, type ApiErrorBody } from "./errors";

/**
 * docs/11-api-spec.md §0 공통 규약 구현.
 * - §0.1 관리자 API는 /api/admin/... 접두어를 쓴다 (호출부에서 path에 포함시킨다)
 * - §0.2 공통 헤더: Authorization, X-Guest-Token, Idempotency-Key
 * - §0.3 공통 응답 형식: 성공은 리소스 그대로, 실패는 {code, message, serverTime, details}
 */
function getApiBaseUrl(): string {
  const url = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!url) {
    throw new Error(
      "NEXT_PUBLIC_API_BASE_URL이 설정되지 않았어요. frontend/.env.local을 확인하세요."
    );
  }
  return url;
}

export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

export interface RequestOptions {
  method?: HttpMethod;
  body?: unknown;
  accessToken?: string | null;
  guestToken?: string | null;
  idempotencyKey?: string;
  /** 리프레시 쿠키가 필요한 요청(재발급·로그아웃)에서만 true로 준다. */
  withCredentials?: boolean;
  signal?: AbortSignal;
}

async function rawRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers({ "Content-Type": "application/json" });
  if (options.accessToken) {
    headers.set("Authorization", `Bearer ${options.accessToken}`);
  }
  if (options.guestToken) {
    headers.set("X-Guest-Token", options.guestToken);
  }
  if (options.idempotencyKey) {
    headers.set("Idempotency-Key", options.idempotencyKey);
  }

  const response = await fetch(`${getApiBaseUrl()}${path}`, {
    method: options.method ?? "GET",
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    credentials: options.withCredentials ? "include" : "same-origin",
    signal: options.signal,
  });

  const guestTokenFromServer = response.headers.get("X-Guest-Token");
  if (guestTokenFromServer) {
    setStoredGuestToken(guestTokenFromServer);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const payload = (await response.json().catch(() => null)) as
    | T
    | ApiErrorBody
    | null;

  if (!response.ok) {
    throw new ApiError(
      response.status,
      (payload as ApiErrorBody) ?? {
        code: "INTERNAL_ERROR",
        message: "서버 응답을 해석하지 못했어요.",
        serverTime: new Date().toISOString(),
      }
    );
  }

  return payload as T;
}

/**
 * Server Component에서 쓰는 함수. 비로그인도 볼 수 있는 조회 화면 전용이다
 * (ARCHITECTURE.md "데이터 페칭 규칙"). 인증 토큰을 붙이지 않는다.
 */
export async function serverGet<T>(
  path: string,
  options: Pick<RequestOptions, "signal"> = {}
): Promise<T> {
  return rawRequest<T>(path, { method: "GET", ...options });
}

export interface TokenProvider {
  getAccessToken: () => string | null;
  /** 진행 중인 재발급이 있으면 그 결과를 공유한다 (직렬화, docs/12 §1.5). 실패하면 null. */
  refresh: () => Promise<string | null>;
}

const tokenProviders: Partial<Record<"customer" | "admin", TokenProvider>> = {};

export function registerTokenProvider(
  scope: "customer" | "admin",
  provider: TokenProvider
): void {
  tokenProviders[scope] = provider;
}

const GUEST_TOKEN_STORAGE_KEY = "savepick.guestToken";

export function getStoredGuestToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(GUEST_TOKEN_STORAGE_KEY);
}

export function setStoredGuestToken(token: string): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(GUEST_TOKEN_STORAGE_KEY, token);
}

export interface ClientRequestOptions
  extends Omit<RequestOptions, "accessToken" | "guestToken"> {
  /** 이 요청이 어떤 인증 스코프를 쓰는지. 인증이 필요 없는 공개 조회는 생략한다. */
  authScope?: "customer" | "admin";
  /** 비로그인 장바구니처럼 게스트 토큰이 필요한 요청이면 true. */
  useGuestToken?: boolean;
}

/**
 * Client Component에서 쓰는 함수. authScope가 있으면 해당 스코프의 액세스 토큰을
 * 붙이고, 401을 받으면 그 스코프의 재발급을 시도해 한 번만 재시도한다.
 */
export async function clientRequest<T>(
  path: string,
  options: ClientRequestOptions = {}
): Promise<T> {
  const provider = options.authScope ? tokenProviders[options.authScope] : undefined;
  const guestToken = options.useGuestToken ? getStoredGuestToken() : undefined;

  const attempt = (accessToken: string | null | undefined) =>
    rawRequest<T>(path, {
      ...options,
      accessToken,
      guestToken,
    });

  try {
    return await attempt(provider?.getAccessToken());
  } catch (error) {
    const isAuthEndpoint = path.startsWith("/api/auth/");
    if (
      error instanceof ApiError &&
      error.code === "UNAUTHENTICATED" &&
      provider &&
      !isAuthEndpoint
    ) {
      const newAccessToken = await provider.refresh();
      if (newAccessToken) {
        return attempt(newAccessToken);
      }
    }
    throw error;
  }
}

export { ApiError } from "./errors";
