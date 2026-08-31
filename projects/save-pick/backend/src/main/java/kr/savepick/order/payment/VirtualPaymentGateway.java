package kr.savepick.order.payment;

/**
 * BR-011 — 가상 결제 판정기. 실제 금전 이동이 없고, 지연 없이 즉시 성공·실패를 반환한다
 * (13번 §4 IC-A4 — 무응답조차 즉시 {@code TIMEOUT} 실패로 반환해 재고 행 락 보유 시간을
 * 밀리초 단위로 유지한다). 외부 호출이 없으므로 트랜잭션 안에서 호출해도 안전하다(13번 §7.2).
 */
public interface VirtualPaymentGateway {

    PaymentJudgement judge(Long orderId, int amount);

    record PaymentJudgement(boolean succeeded, PaymentFailureReason failureReason) {
        public static PaymentJudgement success() {
            return new PaymentJudgement(true, null);
        }

        public static PaymentJudgement failure(PaymentFailureReason reason) {
            return new PaymentJudgement(false, reason);
        }
    }
}
