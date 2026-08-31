import type { ButtonHTMLAttributes, ReactNode } from "react";

export type ButtonVariant = "primary" | "secondary" | "text" | "danger";

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary: "h-[52px] bg-brand text-on-brand",
  secondary: "h-12 bg-surface text-text border border-border",
  text: "h-11 bg-transparent text-brand",
  danger: "h-12 bg-surface text-danger border border-danger",
};

const DISABLED_CLASSES = "bg-border text-text-weak border-0 cursor-not-allowed";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  /**
   * docs/09-ui-design-brief.md §2.1: 비활성 버튼은 숨기지 않고, 왜 못 누르는지
   * 버튼 위 캡션으로 보여준다.
   */
  disabledReason?: ReactNode;
}

/** docs/09-ui-design-brief.md §2.1 버튼 규격. */
export function Button({
  variant = "primary",
  disabled,
  disabledReason,
  className = "",
  children,
  ...rest
}: ButtonProps) {
  const stateClasses = disabled ? DISABLED_CLASSES : VARIANT_CLASSES[variant];

  return (
    <div className="flex flex-col gap-2">
      {disabled && disabledReason ? (
        <p className="font-caption text-text-weak">{disabledReason}</p>
      ) : null}
      <button
        type="button"
        disabled={disabled}
        className={`font-body w-full rounded-md px-5 font-medium transition-colors ${stateClasses} ${className}`}
        {...rest}
      >
        {children}
      </button>
    </div>
  );
}
