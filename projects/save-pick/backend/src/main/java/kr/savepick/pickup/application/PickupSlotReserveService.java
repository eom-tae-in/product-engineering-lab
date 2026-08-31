package kr.savepick.pickup.application;

import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.pickup.domain.PickupSlot;
import kr.savepick.pickup.domain.PickupSlotRepository;
import kr.savepick.pickup.domain.SlotCapacity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 13번 §8 — 픽업 시간대 정원 점유·반납. 결제 성공(S4)·취소·노쇼 반납은 2단계(order 결제·취소
 * 서비스)의 몫이라 이번 슬라이스에서는 이 서비스를 실제로 호출하는 곳이 없다(order 도메인
 * 1단계는 시간대를 "지정"만 하고 점유하지 않는다, 05 §8, A10). 메서드 자체는 지금 완성해
 * 단위/통합 테스트로 직접 호출해 검증한다.
 */
@Service
public class PickupSlotReserveService {

    private final PickupSlotRepository pickupSlotRepository;

    public PickupSlotReserveService(PickupSlotRepository pickupSlotRepository) {
        this.pickupSlotRepository = pickupSlotRepository;
    }

    /** PENDING → CONFIRMED(결제 성공) 시점에만 호출한다(05 §8). */
    @Transactional
    public PickupSlot occupy(Long slotId) {
        PickupSlot slot = pickupSlotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));
        if (!SlotCapacity.of(slot).hasRoom()) {
            throw new BusinessException(ErrorCode.SLOT_FULL);
        }
        pickupSlotRepository.incrementReservedCount(slotId);
        return pickupSlotRepository.findById(slotId).orElseThrow();
    }

    /** CONFIRMED/READY → CANCELED·NO_SHOW 시점에 호출한다(05 §8). 0 미만으로 내려가지 않는다. */
    @Transactional
    public PickupSlot release(Long slotId) {
        pickupSlotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));
        pickupSlotRepository.decrementReservedCount(slotId);
        return pickupSlotRepository.findById(slotId).orElseThrow();
    }
}
