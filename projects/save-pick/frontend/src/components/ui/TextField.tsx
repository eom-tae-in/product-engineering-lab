import type { InputHTMLAttributes } from "react";

export interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  id: string;
  label: string;
  /** 필드 아래 danger색 캡션으로 보여준다. 없으면 오류가 아니다. */
  error?: string;
}

/**
 * docs/09-ui-design-brief.md §2.8 입력 필드 규격.
 * 높이 52px, 라벨은 필드 위 캡션, 포커스 시 브랜드색 테두리, 오류 시 danger색
 * 테두리 + 필드 아래 danger색 캡션. 플레이스홀더를 라벨 대신 쓰지 않는다.
 */
export function TextField({ id, label, error, className = "", ...rest }: TextFieldProps) {
  const errorId = `${id}-error`;

  return (
    <div className="mb-4">
      <label htmlFor={id} className="font-caption mb-1 block text-text-weak">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className={`font-body h-[52px] w-full rounded-md border bg-surface px-3 text-text outline-none transition-colors disabled:bg-bg disabled:text-text-weak ${
          error ? "border-danger" : "border-border focus:border-brand"
        } ${className}`}
        {...rest}
      />
      {error ? (
        <p id={errorId} className="font-caption mt-1 text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
}
