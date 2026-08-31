package kr.savepick.order.payment;

/**
 * BR-012 — 결제 재시도 한도 3회. 순수 정책이므로 시각·DB를 참조하지 않는다.
 */
public final class PaymentRetryPolicy {

    private PaymentRetryPolicy() {
    }

    /** 이번 시도가 마지막 허용 시도인가(실패 시 즉시 선점 해제, BR-012-3). */
    public static boolean isFinalAttempt(int attemptNo, int maxAttempts) {
        return attemptNo >= maxAttempts;
    }

    /** 다음 시도 번호가 한도를 넘는가 — 넘으면 시도 기록을 만들지 않는다(BR-012-2). */
    public static boolean exceedsLimit(int nextAttemptNo, int maxAttempts) {
        return nextAttemptNo > maxAttempts;
    }
}
