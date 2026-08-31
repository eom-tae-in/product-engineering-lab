import { AuthProvider } from "@/lib/auth/customer-auth";
import { BottomTabBar } from "./_components/BottomTabBar";
import { ServerTimeSync } from "./_components/ServerTimeSync";

/**
 * 고객 화면 공통 레이아웃. 하단 탭 바를 공유한다(docs/09 §1.2).
 * 로그인이 필요한 개별 화면(장바구니 이후 구매 흐름, 마이페이지)의 접근 제어는
 * 이 레이아웃이 아니라 각 화면이 useAuth()로 직접 한다 — 비로그인도 볼 수 있는
 * 화면(SC-001~003, SC-015)이 같은 레이아웃을 쓰기 때문이다.
 */
export default function CustomerLayout({ children }: LayoutProps<"/">) {
  return (
    <AuthProvider>
      <ServerTimeSync />
      <div className="flex flex-1 flex-col">
        <main className="flex-1 overflow-y-auto">{children}</main>
        <BottomTabBar />
      </div>
    </AuthProvider>
  );
}
