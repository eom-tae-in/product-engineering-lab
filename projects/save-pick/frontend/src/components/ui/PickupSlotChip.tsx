export type PickupSlotChipStatus =
  | "selectable"
  | "selected"
  | "reservationClosed"
  | "full"
  | "afterProductClosing"
  | "blocked";

const STATUS_CLASSES: Record<PickupSlotChipStatus, string> = {
  selectable: "border-border bg-surface text-text",
  selected: "border-brand bg-brand text-on-brand",
  reservationClosed: "border-border bg-bg text-text-weak opacity-50",
  full: "border-border bg-bg text-text-weak opacity-50",
  afterProductClosing: "border-border bg-bg text-text-weak opacity-50",
  blocked: "border-border bg-bg text-text-weak opacity-50",
};

const SELECTABLE_STATUSES: readonly PickupSlotChipStatus[] = ["selectable", "selected"];

export interface PickupSlotChipProps {
  status: PickupSlotChipStatus;
  /** `20:00~20:30` 형태. */
  timeLabel: string;
  /** 보조 라벨. `4/20` · `마감` · `정원 20/20` · `상품 마감 이후` · `운영 중단`. */
  secondaryLabel: string;
  onSelect?: () => void;
}

/**
 * docs/09-ui-design-brief.md §2.6 픽업 시간대 칩 (SC-006).
 * 100 × 56px, 3열 그리드(그리드 배치는 부모가 한다). 선택 불가 칩은 불투명도 0.5로
 * 낮추되 목록에서 지우지 않는다 — 왜 못 고르는지 보조 라벨로 항상 보여준다.
 */
export function PickupSlotChip({
  status,
  timeLabel,
  secondaryLabel,
  onSelect,
}: PickupSlotChipProps) {
  const selectable = SELECTABLE_STATUSES.includes(status);

  return (
    <button
      type="button"
      onClick={onSelect}
      disabled={!selectable}
      aria-pressed={status === "selected"}
      aria-label={`${timeLabel} ${secondaryLabel}`}
      className={`flex h-14 w-[100px] flex-col items-center justify-center gap-0.5 rounded-md border transition-colors disabled:cursor-not-allowed ${STATUS_CLASSES[status]}`}
    >
      <span className="font-caption font-medium">{timeLabel}</span>
      <span className="font-caption">{secondaryLabel}</span>
    </button>
  );
}
