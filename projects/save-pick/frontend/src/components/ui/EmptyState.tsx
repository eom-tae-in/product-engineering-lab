import type { ReactNode } from "react";

export interface EmptyStateProps {
  /** 사실 문구. docs/06이 화면별로 못박은 문구를 그대로 넣는다. */
  message: string;
  /** 이유·조건. 없는 화면도 있다. */
  reason?: string;
  action?: ReactNode;
}

/** docs/09-ui-design-brief.md §3.2: 사실 문구 + 이유·조건 + 다음 행동 버튼. */
export function EmptyState({ message, reason, action }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center gap-3 py-8 text-center">
      <p className="font-body text-text">{message}</p>
      {reason ? <p className="font-caption text-text-weak">{reason}</p> : null}
      {action}
    </div>
  );
}
