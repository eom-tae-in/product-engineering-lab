export type MemberRole = "CUSTOMER" | "ADMIN";

export interface AuthUser {
  memberId: number;
  name: string;
  role: MemberRole;
}

export type AuthStatus = "checking" | "authenticated" | "guest";

export interface AuthState {
  status: AuthStatus;
  user: AuthUser | null;
  accessToken: string | null;
}

export interface LoginResponse {
  memberId: number;
  name: string;
  role: MemberRole;
  accessToken: string;
  accessTokenExpiresAt: string;
}

export interface RefreshResponse {
  accessToken: string;
  accessTokenExpiresAt: string;
}
