package kr.savepick.pickup.application;

import java.time.LocalDate;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.pickup.domain.PickupNumberSeqRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BR-026 — 영업일 내 3자리 픽업 번호 발급. CONFIRMED 도달 시점(결제 성공)에만 호출한다.
 * 이번 슬라이스는 결제(API-022, 2단계)를 만들지 않으므로 이 클래스를 실제로 호출하는 곳이 없다 —
 * 갱신 로직만 지금 완성해 단위/통합 테스트로 직접 검증한다.
 */
@Service
public class PickupNumberIssuer {

    private final PickupNumberSeqRepository pickupNumberSeqRepository;

    public PickupNumberIssuer(PickupNumberSeqRepository pickupNumberSeqRepository) {
        this.pickupNumberSeqRepository = pickupNumberSeqRepository;
    }

    @Transactional
    public short issue(LocalDate businessDate) {
        return pickupNumberSeqRepository.incrementAndGet(businessDate)
                .orElseThrow(() -> new BusinessException(ErrorCode.PICKUP_NUMBER_EXHAUSTED));
    }
}
