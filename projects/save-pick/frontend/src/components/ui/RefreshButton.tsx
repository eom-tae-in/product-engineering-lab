"use client";

import { useRouter } from "next/navigation";
import { Button, type ButtonProps } from "./Button";

export interface RefreshButtonProps extends Omit<ButtonProps, "onClick" | "children"> {
  label?: string;
}

/**
 * 비로그인도 보는 조회 화면(SC-001·002·003·015 등)은 Server Component에서 직접
 * fetch하므로 오류 시 재조회를 트리거할 클라이언트 함수를 서버 쪽에서 만들 수 없다
 * (함수는 서버→클라이언트 경계를 건널 수 없다, RSC 직렬화 제약). 대신 이 버튼이
 * `router.refresh()`로 같은 라우트 세그먼트를 다시 렌더링해 재조회를 일으킨다.
 */
export function RefreshButton({
  label = "다시 시도",
  variant = "secondary",
  className = "w-auto px-6",
  ...rest
}: RefreshButtonProps) {
  const router = useRouter();
  return (
    <Button variant={variant} className={className} onClick={() => router.refresh()} {...rest}>
      {label}
    </Button>
  );
}
