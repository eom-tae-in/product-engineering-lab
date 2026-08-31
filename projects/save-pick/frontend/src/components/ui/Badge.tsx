import type { ReactNode } from "react";

export type BadgeTone = "discount" | "low-stock" | "soldout" | "closed";

const TONE_CLASSES: Record<BadgeTone, string> = {
  discount: "bg-brand-weak text-brand",
  "low-stock": "bg-transparent text-warning",
  soldout: "bg-danger-weak text-danger",
  closed: "bg-border text-text-weak",
};

export interface BadgeProps {
  tone: BadgeTone;
  children: ReactNode;
}

/** docs/09-ui-design-brief.md §2.2 배지 규격. */
export function Badge({ tone, children }: BadgeProps) {
  return (
    <span
      className={`font-caption inline-flex h-[22px] items-center rounded-sm px-2 ${TONE_CLASSES[tone]}`}
    >
      {children}
    </span>
  );
}
