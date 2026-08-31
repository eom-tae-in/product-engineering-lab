/**
 * docs/11-api-spec.md §0.5 오류 코드 카탈로그 전체.
 * 여기 없는 코드가 응답에 오면 백엔드가 §0.5에 없는 코드를 새로 만든 것이므로
 * 화면에서 처리하지 말고 보고한다.
 */
export const ERROR_CODES = [
  "VALIDATION_ERROR",
  "UNAUTHENTICATED",
  "FORBIDDEN",
  "NOT_FOUND",
  "EMAIL_DUPLICATED",
  "INVALID_CREDENTIALS",
  "LOGIN_BLOCKED",
  "PRODUCT_NOT_ON_SALE",
  "PRODUCT_CLOSED",
  "MAX_QUANTITY_EXCEEDED",
  "CART_ITEM_LIMIT_EXCEEDED",
  "CART_EMPTY",
  "CART_HAS_UNAVAILABLE_ITEM",
  "OUT_OF_STOCK",
  "PENDING_ORDER_EXISTS",
  "ORDER_RESTRICTED",
  "HOLD_EXPIRED",
  "INVALID_ORDER_STATUS",
  "SLOT_NOT_FOUND",
  "SLOT_CLOSED",
  "SLOT_FULL",
  "SLOT_AFTER_PRODUCT_CLOSING",
  "SLOT_DATE_OUT_OF_RANGE",
  "SLOT_NOT_SELECTED",
  "AMOUNT_MISMATCH",
  "PAYMENT_ATTEMPT_EXCEEDED",
  "PAYMENT_FAILED",
  "ALREADY_PAID",
  "CANCEL_DEADLINE_PASSED",
  "CANCEL_NOT_ALLOWED",
  "CANCEL_REASON_REQUIRED",
  "STOCK_BELOW_COMMITTED",
  "PRODUCT_STATUS_TRANSITION_DENIED",
  "CLOSING_TIME_INVALID",
  "BUSINESS_HOUR_INVALID",
  "PICKUP_NUMBER_EXHAUSTED",
  "INTERNAL_ERROR",
] as const;

export type ErrorCode = (typeof ERROR_CODES)[number];

export function isKnownErrorCode(code: string): code is ErrorCode {
  return (ERROR_CODES as readonly string[]).includes(code);
}

/**
 * 화면이 이 코드를 따로 처리하지 않을 때 보여줄 기본 문구.
 * docs/06-screen-list.md가 화면별로 문구를 못박은 코드는 그 화면 컴포넌트가
 * 직접 분기해서 이 기본값을 덮어써야 한다 (예: CANCEL_NOT_ALLOWED의 SC-011 3분기).
 */
export const DEFAULT_ERROR_MESSAGES: Record<ErrorCode, string> = {
  VALIDATION_ERROR: "입력한 내용을 확인해주세요.",
  UNAUTHENTICATED: "로그인이 필요해요.",
  FORBIDDEN: "접근 권한이 없어요.",
  NOT_FOUND: "찾을 수 없어요.",
  EMAIL_DUPLICATED: "이미 가입된 이메일이에요.",
  INVALID_CREDENTIALS: "이메일 또는 비밀번호를 확인해주세요.",
  LOGIN_BLOCKED: "로그인 시도가 많아 잠시 후 다시 시도할 수 있어요.",
  PRODUCT_NOT_ON_SALE: "지금은 판매 중이 아닌 상품이에요.",
  PRODUCT_CLOSED: "판매가 종료된 상품이에요.",
  MAX_QUANTITY_EXCEEDED: "1회 주문 최대 수량을 넘었어요.",
  CART_ITEM_LIMIT_EXCEEDED:
    "장바구니에는 품목을 10개까지 담을 수 있어요. 정리한 뒤 다시 담아주세요.",
  CART_EMPTY: "장바구니가 비어 있어요.",
  CART_HAS_UNAVAILABLE_ITEM: "구매할 수 없는 품목을 정리하면 주문할 수 있어요.",
  OUT_OF_STOCK: "요청한 수량만큼 재고가 남아 있지 않아요.",
  PENDING_ORDER_EXISTS: "진행 중인 주문서가 있어요.",
  ORDER_RESTRICTED: "노쇼 누적으로 지금은 새 주문을 만들 수 없어요.",
  HOLD_EXPIRED: "선점 시간이 끝났어요.",
  INVALID_ORDER_STATUS: "지금 상태에서는 할 수 없는 조작이에요.",
  SLOT_NOT_FOUND: "존재하지 않는 시간대예요.",
  SLOT_CLOSED: "선택한 시간대의 예약이 마감됐어요.",
  SLOT_FULL: "선택한 시간대의 정원이 찼어요.",
  SLOT_AFTER_PRODUCT_CLOSING: "상품 마감 이후 시간대는 고를 수 없어요.",
  SLOT_DATE_OUT_OF_RANGE: "선택할 수 없는 날짜예요.",
  SLOT_NOT_SELECTED: "픽업 시간대를 먼저 선택해주세요.",
  AMOUNT_MISMATCH: "주문 금액이 달라졌어요. 주문서를 다시 확인해주세요.",
  PAYMENT_ATTEMPT_EXCEEDED: "결제 시도 횟수를 모두 사용했어요.",
  PAYMENT_FAILED: "결제가 완료되지 않았어요.",
  ALREADY_PAID: "이미 결제가 완료된 주문이에요.",
  CANCEL_DEADLINE_PASSED: "취소 가능 시각이 지났어요.",
  CANCEL_NOT_ALLOWED: "지금 상태에서는 취소할 수 없어요.",
  CANCEL_REASON_REQUIRED: "취소 사유를 입력해주세요.",
  STOCK_BELOW_COMMITTED: "이미 선점·확정된 수량보다 적게 줄일 수 없어요.",
  PRODUCT_STATUS_TRANSITION_DENIED: "허용되지 않는 상태 전환이에요.",
  CLOSING_TIME_INVALID: "마감 시각을 확인해주세요.",
  BUSINESS_HOUR_INVALID: "영업시간을 확인해주세요.",
  PICKUP_NUMBER_EXHAUSTED: "오늘 픽업 번호를 모두 사용했어요.",
  INTERNAL_ERROR:
    "일시적인 오류로 처리하지 못했어요. 잠시 뒤 다시 시도해주세요.",
};

export interface ApiErrorBody {
  code: string;
  message: string;
  serverTime: string;
  details?: Record<string, unknown>;
}

export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly serverTime: string;
  readonly details?: Record<string, unknown>;

  constructor(status: number, body: ApiErrorBody) {
    super(body.message);
    this.name = "ApiError";
    this.status = status;
    this.code = body.code;
    this.serverTime = body.serverTime;
    this.details = body.details;
  }

  /** 06번이 화면별 문구를 정하지 않은 경우에 쓰는 기본 문구. */
  get defaultMessage(): string {
    return isKnownErrorCode(this.code)
      ? DEFAULT_ERROR_MESSAGES[this.code]
      : this.message;
  }
}
