import type { ReactNode } from "react";
import { Button } from "./Button";

export interface ErrorStateProps {
  /** 무엇이 안 됐는지 + 왜. 기술 용어(500, timeout, null)를 쓰지 않는다. */
  message: string;
  onRetry?: () => void;
  retryLabel?: string;
  action?: ReactNode;
}

/**
 * docs/09-ui-design-brief.md §3.3 "조회 실패" 표현: 콘텐츠 영역을 이 컴포넌트로
 * 대체한다. 직전 데이터가 있으면 지우지 않는 것은 호출부(부모)의 책임이다 —
 * 이 컴포넌트는 대체할지 말지 스스로 판단하지 않는다.
 */
export function ErrorState({
  message,
  onRetry,
  retryLabel = "다시 시도",
  action,
}: ErrorStateProps) {
  return (
    <div className="flex flex-col items-center gap-3 py-8 text-center">
      <p className="font-body text-text">{message}</p>
      {onRetry ? (
        <Button variant="secondary" onClick={onRetry} className="w-auto px-6">
          {retryLabel}
        </Button>
      ) : null}
      {action}
    </div>
  );
}
