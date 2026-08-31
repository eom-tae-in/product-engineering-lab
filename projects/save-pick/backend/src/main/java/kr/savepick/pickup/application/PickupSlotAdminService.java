package kr.savepick.pickup.application;

import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.pickup.domain.PickupSlot;
import kr.savepick.pickup.domain.PickupSlotRepository;
import kr.savepick.pickup.domain.SlotCapacity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-119 개별 시간대 정원 변경·차단 (BR-016, FR-057, FR-058).
 */
@Service
public class PickupSlotAdminService {

    private final PickupSlotRepository pickupSlotRepository;

    public PickupSlotAdminService(PickupSlotRepository pickupSlotRepository) {
        this.pickupSlotRepository = pickupSlotRepository;
    }

    /**
     * @param capacity null이면 정원을 바꾸지 않는다.
     * @param blocked  null이면 차단 상태를 바꾸지 않는다.
     */
    @Transactional
    public UpdateResult update(Long slotId, Short capacity, Boolean blocked) {
        PickupSlot slot = pickupSlotRepository.findById(slotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));

        if (capacity != null) {
            slot.updateCapacity(capacity);
        }
        if (blocked != null) {
            if (blocked) {
                slot.block();
            } else {
                slot.unblock();
            }
        }
        slot = pickupSlotRepository.save(slot);

        SlotCapacity slotCapacity = SlotCapacity.of(slot);
        return new UpdateResult(slot, slotCapacity.isOverCapacity(), slot.getReservedCount());
    }

    public record UpdateResult(PickupSlot slot, boolean overCapacity, int keptOrderCount) {
    }
}
