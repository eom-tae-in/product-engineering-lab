/**
 * 타입만 다시 내보낸다. Provider 컴포넌트(AuthProvider/AdminAuthProvider)는
 * 여기서 재수출하지 않는다 — 고객·관리자 번들이 서로의 인증 코드를 끌어안지
 * 않도록 각 레이아웃이 "./customer-auth" / "./admin-auth"를 직접 import한다.
 */
export type { AuthUser, AuthState, AuthStatus, MemberRole } from "./types";
