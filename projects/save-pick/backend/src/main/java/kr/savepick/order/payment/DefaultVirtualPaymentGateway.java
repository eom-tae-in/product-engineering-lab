package kr.savepick.order.payment;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Component;

/**
 * 기본 정책 — 실제 결제 연동이 없는 데모 환경에서 무작위 실패로 결제 흐름을 방해하지 않도록
 * 기본값은 <b>항상 성공</b>이다(문서에 실제 판정 정책이 없어 임의로 정함 — 가정, 2단계 구현
 * 보고 참고). 성공률·무응답 등 다른 정책이 필요해지면 이 구현체만 교체하면 된다
 * (13번 §4 IC-A4 — 지연 없이 즉시 판정).
 *
 * <p>테스트에서 결제 실패·무응답 경로(TC-047~050)를 결정론적으로 검증해야 하므로,
 * {@link #forceNextResult}로 다음 {@code judge} 호출 1회에 대한 결과를 큐에 미리 넣어 둘 수
 * 있다. 큐가 비어 있으면 기본 정책(항상 성공)으로 되돌아간다.
 */
@Component
public class DefaultVirtualPaymentGateway implements VirtualPaymentGateway {

    private final Deque<PaymentJudgement> forcedResults = new ConcurrentLinkedDeque<>();

    @Override
    public PaymentJudgement judge(Long orderId, int amount) {
        PaymentJudgement forced = forcedResults.pollFirst();
        return forced != null ? forced : PaymentJudgement.success();
    }

    /** 테스트 전용 훅 — 다음 판정 결과를 결정론적으로 강제한다(여러 번 호출하면 순서대로 소비된다). */
    public void forceNextResult(PaymentJudgement judgement) {
        forcedResults.addLast(judgement);
    }

    /** 테스트 간 상태가 새어나가지 않도록 큐를 비운다. */
    public void reset() {
        forcedResults.clear();
    }
}
