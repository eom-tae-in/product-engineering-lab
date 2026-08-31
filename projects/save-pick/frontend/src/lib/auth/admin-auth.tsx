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
import { clientRequest, registerTokenProvider } from "@/lib/api-client";
import type { AuthState, AuthUser, LoginResponse, RefreshResponse } from "./types";

interface AdminAuthContextValue extends AuthState {
  login: (email: string, password: string) => Promise<AuthUser>;
  logout: () => Promise<void>;
}

const AdminAuthContext = createContext<AdminAuthContextValue | null>(null);

/**
 * 고객 세션(customer-auth.tsx)과 별도 인스턴스다. 로그인은 API-101
 * `/api/admin/auth/login` 전용 경로를 쓴다. 재발급·로그아웃은 API-003·004를
 * 공유한다 — 세션 저장소(`auth_sessions`)가 역할 구분 없이 하나이기 때문이다
 * (docs/12-auth.md). 재발급 응답에는 사용자 정보가 없어 새로고침 직후에는
 * `user`가 비어 있을 수 있다 — 관리자 화면은 이름이 아니라 `status`로만 게이팅한다
 * (관리자 프로필 조회 API가 11번 카탈로그에 없다, ARCHITECTURE.md FE-A 참고).
 */
export function AdminAuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    status: "checking",
    user: null,
    accessToken: null,
  });
  const accessTokenRef = useRef<string | null>(null);
  const inFlightRefresh = useRef<Promise<string | null> | null>(null);

  const clearSession = useCallback(() => {
    accessTokenRef.current = null;
    setState({ status: "guest", user: null, accessToken: null });
  }, []);

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
    registerTokenProvider("admin", {
      getAccessToken: () => accessTokenRef.current,
      refresh,
    });
  }, [refresh]);

  useEffect(() => {
    refresh().then((token) => {
      if (!token) clearSession();
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const login = useCallback(
    async (email: string, password: string): Promise<AuthUser> => {
      const res = await clientRequest<LoginResponse>("/api/admin/auth/login", {
        method: "POST",
        withCredentials: true,
        body: { email, password },
      });
      const user: AuthUser = {
        memberId: res.memberId,
        name: res.name,
        role: res.role,
      };
      accessTokenRef.current = res.accessToken;
      setState({ status: "authenticated", user, accessToken: res.accessToken });
      return user;
    },
    []
  );

  const logout = useCallback(async () => {
    try {
      await clientRequest<void>("/api/auth/logout", {
        method: "POST",
        authScope: "admin",
        withCredentials: true,
      });
    } finally {
      clearSession();
    }
  }, [clearSession]);

  return (
    <AdminAuthContext.Provider value={{ ...state, login, logout }}>
      {children}
    </AdminAuthContext.Provider>
  );
}

export function useAdminAuth(): AdminAuthContextValue {
  const ctx = useContext(AdminAuthContext);
  if (!ctx) {
    throw new Error("useAdminAuth는 <AdminAuthProvider> 안에서만 쓸 수 있어요.");
  }
  return ctx;
}
