export interface QuantityStepperProps {
  value: number;
  /** 기본 1. 장바구니처럼 0까지 내려 삭제로 이어지는 화면은 0을 준다. */
  min?: number;
  max: number;
  onChange: (nextValue: number) => void;
  /** 전체 조절기를 잠글 때(구매 불가 품목 등) true. */
  disabled?: boolean;
  decreaseLabel?: string;
  increaseLabel?: string;
}

/**
 * docs/09-ui-design-brief.md §2.8 수량 조절기 규격.
 * `−` / 숫자 / `+`, 각 44 × 44px, 한계값(min·max) 도달 시 해당 버튼만 비활성.
 * SC-003(상품 상세)·SC-004(장바구니)가 함께 쓴다.
 */
export function QuantityStepper({
  value,
  min = 1,
  max,
  onChange,
  disabled = false,
  decreaseLabel = "수량 줄이기",
  increaseLabel = "수량 늘리기",
}: QuantityStepperProps) {
  const atMin = value <= min;
  const atMax = value >= max;

  return (
    <div className="flex items-center gap-2">
      <button
        type="button"
        aria-label={decreaseLabel}
        onClick={() => onChange(Math.max(min, value - 1))}
        disabled={disabled || atMin}
        className="h-11 w-11 rounded-md border border-border text-text disabled:opacity-50"
      >
        −
      </button>
      <span className="font-body w-6 text-center tabular-nums">{value}</span>
      <button
        type="button"
        aria-label={increaseLabel}
        onClick={() => onChange(Math.min(max, value + 1))}
        disabled={disabled || atMax}
        className="h-11 w-11 rounded-md border border-border text-text disabled:opacity-50"
      >
        +
      </button>
    </div>
  );
}
