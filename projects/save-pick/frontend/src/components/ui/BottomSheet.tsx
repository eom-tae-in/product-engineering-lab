"use client";

import type { ReactNode } from "react";

export interface BottomSheetProps {
  open: boolean;
  onClose?: () => void;
  /**
   * docs/09-ui-design-brief.md §2.7: 선점 만료 시트처럼 딤을 탭해도 닫히지 않아야 하는
   * 차단 안내에서 false로 준다. 기본은 true(확인 요구·결과 안내).
   */
  dismissible?: boolean;
  children: ReactNode;
}

/**
 * docs/09-ui-design-brief.md §2.7 하단 시트 규격.
 * 상단 모서리 `--radius-lg`, `--shadow-sheet`, 배경 딤 `rgba(26,31,28,.4)`.
 */
export function BottomSheet({ open, onClose, dismissible = true, children }: BottomSheetProps) {
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex flex-col justify-end">
      <button
        type="button"
        aria-label="닫기"
        tabIndex={dismissible ? 0 : -1}
        onClick={dismissible ? onClose : undefined}
        className="absolute inset-0 h-full w-full cursor-default bg-[rgba(26,31,28,0.4)]"
      />
      <div
        role="dialog"
        aria-modal="true"
        className="relative rounded-t-lg bg-surface p-4 shadow-[var(--shadow-sheet)]"
      >
        {children}
      </div>
    </div>
  );
}
