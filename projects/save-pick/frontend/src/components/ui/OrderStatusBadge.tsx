/** kr.savepick.order.domain.OrderStatus와 1:1로 맞춘다 (8개). */
export type OrderStatus =
  | "PENDING"
  | "CONFIRMED"
  | "READY"
  | "COMPLETED"
  | "CANCELED"
  | "NO_SHOW"
  | "EXPIRED"
  | "FAILED";

interface StatusPresentation {
  customerLabel: string | null;
  adminLabel: string;
  className: string;
}

/** docs/09-ui-design-brief.md §2.3 주문 상태 배지 표기. */
const STATUS_PRESENTATION: Record<OrderStatus, StatusPresentation> = {
  PENDING: {
    customerLabel: null,
    adminLabel: "결제 대기",
    className: "bg-border text-text-weak",
  },
  CONFIRMED: {
    customerLabel: "확정",
    adminLabel: "확정",
    className: "bg-brand-weak text-brand",
  },
  READY: {
    customerLabel: "준비 완료",
    adminLabel: "준비 완료",
    className: "bg-brand-weak text-brand",
  },
  COMPLETED: {
    customerLabel: "픽업 완료",
    adminLabel: "픽업 완료",
    className: "bg-border text-text",
  },
  CANCELED: {
    customerLabel: "취소",
    adminLabel: "취소",
    className: "bg-border text-text-weak",
  },
  NO_SHOW: {
    customerLabel: "노쇼",
    adminLabel: "노쇼",
    className: "bg-danger-weak text-danger",
  },
  EXPIRED: {
    customerLabel: null,
    adminLabel: "선점 만료",
    className: "bg-border text-text-weak",
  },
  FAILED: {
    customerLabel: "결제 실패",
    adminLabel: "결제 실패",
    className: "bg-danger-weak text-danger",
  },
};

export interface OrderStatusBadgeProps {
  status: OrderStatus;
  /** PENDING·EXPIRED는 고객 기본 목록에 노출하지 않는다(docs/06 SC-009). 관리자 화면에서만 true로 준다. */
  audience?: "customer" | "admin";
}

export function OrderStatusBadge({ status, audience = "customer" }: OrderStatusBadgeProps) {
  const presentation = STATUS_PRESENTATION[status];
  const label =
    audience === "admin" ? presentation.adminLabel : presentation.customerLabel;

  if (!label) return null;

  return (
    <span
      className={`font-caption inline-flex h-[22px] items-center rounded-sm px-2 ${presentation.className}`}
    >
      {label}
    </span>
  );
}
