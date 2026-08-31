"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import {
  clientRequest,
  getStoredGuestToken,
  registerTokenProvider,
} from "@/lib/api-client";
import type { AuthState, AuthUser, LoginResponse, RefreshResponse } from "./types";

interface CustomerAuthContextValue extends AuthState {
  /** 로그인 성공(API-002) 또는 회원가입 성공(API-001) 응답으로 세션을 세운다. */
  setSession: (user: AuthUser, accessToken: string) => void;
  login: (email: string, password: string) => Promise<AuthUser>;
  logout: () => Promise<void>;
}

const CustomerAuthContext = createContext<CustomerAuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    status: "checking",
    user: null,
    accessToken: null,
  });
  const accessTokenRef = useRef<string | null>(null);
  const inFlightRefresh = useRef<Promise<string | null> | null>(null);

  const setSession = useCallback((user: AuthUser, accessToken: string) => {
    accessTokenRef.current = accessToken;
    setState({ status: "authenticated", user, accessToken });
  }, []);

  const clearSession = useCallback(() => {
    accessTokenRef.current = null;
    setState({ status: "guest", user: null, accessToken: null });
  }, []);

  // docs/12-auth.md §1.5: 여러 탭이 동시에 재발급을 시도해도 진행 중인 요청 하나의
  // 결과를 공유한다. 재발급도 401이면 로그인 상태를 종료한다.
  const refresh = useCallback(async (): Promise<string | null> => {
    if (inFlightRefresh.current) {
      return inFlightRefresh.current;
    }
    const task = (async () => {
      try {
        const res = await clientRequest<RefreshResponse>(
          "/api/auth/token/refresh",
          { method: "POST", withCredentials: true }
        );
        accessTokenRef.current = res.accessToken;
        setState((prev) => ({
          status: "authenticated",
          // API-003 응답에는 사용자 정보가 없다. 화면이 필요하면 GET /api/me로 채운다.
          user: prev.user,
          accessToken: res.accessToken,
        }));
        return res.accessToken;
      } catch {
        clearSession();
        return null;
      } finally {
        inFlightRefresh.current = null;
      }
    })();
    inFlightRefresh.current = task;
    return task;
  }, [clearSession]);

  useEffect(() => {
    registerTokenProvider("customer", {
      getAccessToken: () => accessTokenRef.current,
      refresh,
    });
  }, [refresh]);

  // 앱 최초 로드 시 리프레시 쿠키로 세션 복원을 한 번 시도한다.
  useEffect(() => {
    refresh().then((token) => {
      if (!token) clearSession();
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const login = useCallback(
    async (email: string, password: string): Promise<AuthUser> => {
      const res = await clientRequest<LoginResponse>("/api/auth/login", {
        method: "POST",
        withCredentials: true,
        body: { email, password, guestToken: getStoredGuestToken() ?? undefined },
      });
      const user: AuthUser = {
        memberId: res.memberId,
        name: res.name,
        role: res.role,
      };
      setSession(user, res.accessToken);
      return user;
    },
    [setSession]
  );

  const logout = useCallback(async () => {
    try {
      await clientRequest<void>("/api/auth/logout", {
        method: "POST",
        authScope: "customer",
        withCredentials: true,
      });
    } finally {
      clearSession();
    }
  }, [clearSession]);

  return (
    <CustomerAuthContext.Provider value={{ ...state, setSession, login, logout }}>
      {children}
    </CustomerAuthContext.Provider>
  );
}

export function useAuth(): CustomerAuthContextValue {
  const ctx = useContext(CustomerAuthContext);
  if (!ctx) {
    throw new Error("useAuth는 <AuthProvider> 안에서만 쓸 수 있어요.");
  }
  return ctx;
}
