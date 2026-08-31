package kr.savepick.order.payment;

/**
 * 10-erd.md payment_attempts.failure_reason — {@code CHK_payment_attempts_failure_reason}과
 * 문자 그대로 일치한다. {@code TIMEOUT}은 실제로 기다린 결과가 아니라 가상 결제 판정기가 무응답을
 * 표현하기 위해 즉시 돌려주는 값이다(BR-011, 13번 §4 "무응답의 처리").
 */
public enum PaymentFailureReason {
    DECLINED,
    TIMEOUT,
    SYSTEM_ERROR
}
