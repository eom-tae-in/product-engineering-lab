"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { AdminAuthProvider, useAdminAuth } from "@/lib/auth/admin-auth";
import { Skeleton } from "@/components/ui/Skeleton";

const ADMIN_LOGIN_PATH = "/admin/login";

/**
 * 미인증 관리자는 SC-101(관리자 로그인)로 보낸다(docs/06 §4 공통 규칙).
 * 로그인 화면 자체는 이 가드를 적용하지 않는다 — 적용하면 리다이렉트 루프가 된다.
 */
function AdminGate({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { status } = useAdminAuth();
  const isLoginPage = pathname === ADMIN_LOGIN_PATH;

  useEffect(() => {
    if (!isLoginPage && status === "guest") {
      router.replace(ADMIN_LOGIN_PATH);
    }
  }, [isLoginPage, status, router]);

  if (isLoginPage) {
    return <>{children}</>;
  }

  if (status !== "authenticated") {
    return (
      <div className="p-4">
        <Skeleton className="h-6 w-40" />
      </div>
    );
  }

  return <>{children}</>;
}

export default function AdminLayout({ children }: LayoutProps<"/admin">) {
  return (
    <AdminAuthProvider>
      <AdminGate>{children}</AdminGate>
    </AdminAuthProvider>
  );
}
