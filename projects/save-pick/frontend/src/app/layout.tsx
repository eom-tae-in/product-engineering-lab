import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "savePick",
  description: "마감 할인 상품을 예약하고 매장에서 픽업하는 서비스",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className="h-full antialiased">
      <body className="flex min-h-full flex-col bg-bg text-text">{children}</body>
    </html>
  );
}
