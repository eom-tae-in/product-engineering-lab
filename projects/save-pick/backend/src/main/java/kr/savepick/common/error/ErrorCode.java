package kr.savepick.common.error;

import org.springframework.http.HttpStatus;

/**
 * 11-api-spec.md §0.5 오류 코드 카탈로그와 1:1로 대응한다.
 * account, store, product, stock, cart, pickup, order(1단계)에서 쓰는 코드를 담는다.
 * 카탈로그에 없는 값을 추가하지 않는다 — 필요해지면 11번 문서를 먼저 고친다.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    LOGIN_BLOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 5회 실패해 잠시 차단되었습니다."),
    ORDER_RESTRICTED(HttpStatus.FORBIDDEN, "노쇼 누적으로 주문이 제한된 상태입니다."),
    BUSINESS_HOUR_INVALID(HttpStatus.BAD_REQUEST, "영업시간이 올바르지 않습니다. 종료 시각은 시작 시각보다 뒤여야 하고 30분 단위여야 합니다."),
    PRODUCT_NOT_ON_SALE(HttpStatus.CONFLICT, "판매 중인 상품이 아닙니다."),
    PRODUCT_CLOSED(HttpStatus.CONFLICT, "판매가 종료된 상품입니다."),
    MAX_QUANTITY_EXCEEDED(HttpStatus.CONFLICT, "1회 주문 가능한 최대 수량을 초과했습니다."),
    CART_ITEM_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "장바구니에는 품목을 최대 10개까지 담을 수 있습니다."),
    CART_EMPTY(HttpStatus.CONFLICT, "장바구니에 주문할 품목이 없습니다."),
    CART_HAS_UNAVAILABLE_ITEM(HttpStatus.CONFLICT, "구매할 수 없는 품목이 장바구니에 남아 있습니다."),
    OUT_OF_STOCK(HttpStatus.CONFLICT, "요청한 수량만큼 재고가 남아 있지 않습니다."),
    STOCK_BELOW_COMMITTED(HttpStatus.CONFLICT, "선점·확정 판매 수량보다 적게 설정할 수 없습니다."),
    PRODUCT_STATUS_TRANSITION_DENIED(HttpStatus.CONFLICT, "허용되지 않는 상품 상태 전이입니다."),
    CLOSING_TIME_INVALID(HttpStatus.BAD_REQUEST, "마감 시각이 과거이거나 영업 종료 시각을 넘었습니다."),
    PENDING_ORDER_EXISTS(HttpStatus.CONFLICT, "이미 진행 중인 주문서가 있습니다."),
    HOLD_EXPIRED(HttpStatus.CONFLICT, "재고 선점 유효 시간이 지났습니다."),
    INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "현재 주문 상태에서는 처리할 수 없습니다."),
    SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 픽업 시간대입니다."),
    SLOT_CLOSED(HttpStatus.CONFLICT, "예약이 마감되었거나 차단된 시간대입니다."),
    SLOT_FULL(HttpStatus.CONFLICT, "선택한 시간대의 정원이 찼습니다."),
    SLOT_AFTER_PRODUCT_CLOSING(HttpStatus.CONFLICT, "상품 마감 시각 이후에 시작하는 시간대는 선택할 수 없습니다."),
    SLOT_DATE_OUT_OF_RANGE(HttpStatus.CONFLICT, "선택할 수 없는 날짜입니다."),
    SLOT_NOT_SELECTED(HttpStatus.CONFLICT, "픽업 시간대를 먼저 지정해야 합니다."),
    AMOUNT_MISMATCH(HttpStatus.CONFLICT, "결제 요청 금액이 주문서 확정 금액과 다릅니다."),
    PAYMENT_ATTEMPT_EXCEEDED(HttpStatus.CONFLICT, "결제 시도 횟수를 모두 사용했습니다."),
    /**
     * 11번 §0.5 카탈로그의 유일한 200 항목 — 결제 실패는 정상 흐름의 한 갈래라 예외로 던지지
     * 않는다(§0.5 표 하단 설명). {@code GlobalExceptionHandler}를 거치지 않고
     * {@code order/payment} 응답 DTO가 이 상수의 이름만 문자열로 재사용한다.
     */
    PAYMENT_FAILED(HttpStatus.OK, "결제가 실패했습니다."),
    ALREADY_PAID(HttpStatus.CONFLICT, "이미 결제가 완료된 주문입니다."),
    CANCEL_DEADLINE_PASSED(HttpStatus.CONFLICT, "취소 가능 시각이 지났습니다."),
    CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "현재 상태에서는 취소할 수 없습니다."),
    CANCEL_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "취소 사유를 입력해야 합니다."),
    PICKUP_NUMBER_EXHAUSTED(HttpStatus.CONFLICT, "영업일 픽업 번호가 모두 소진되었습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "처리 중 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
