"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

/** docs/06 D7, docs/09 §1.2: 홈 / 장바구니 / 주문 내역 / 마이페이지, 탭당 88×56px. */
const TABS = [
  { href: "/", label: "홈" },
  { href: "/cart", label: "장바구니" },
  { href: "/orders", label: "주문 내역" },
  { href: "/me", label: "마이페이지" },
] as const;

export function BottomTabBar() {
  const pathname = usePathname();

  return (
    <nav
      aria-label="하단 탭"
      className="sticky bottom-0 flex h-14 border-t border-border bg-surface"
    >
      {TABS.map((tab) => {
        const isActive = tab.href === "/" ? pathname === "/" : pathname.startsWith(tab.href);
        return (
          <Link
            key={tab.href}
            href={tab.href}
            aria-current={isActive ? "page" : undefined}
            className={`font-caption flex w-[88px] flex-1 flex-col items-center justify-center gap-1 ${
              isActive ? "text-brand" : "text-text-weak"
            }`}
          >
            <span aria-hidden className="h-5 w-5 rounded-full border-2 border-current" />
            {tab.label}
          </Link>
        );
      })}
    </nav>
  );
}
