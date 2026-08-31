package kr.savepick.stock.domain;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryHoldRepository {

    InventoryHold save(InventoryHold hold);

    List<InventoryHold> findByOrderId(Long orderId);

    List<InventoryHold> findByOrderIdAndStatus(Long orderId, HoldStatus status);

    List<InventoryHold> findByProductIdAndStatusAndExpiresAtLessThanEqual(
            Long productId, HoldStatus status, LocalDateTime now);

    /**
     * 14-project-structure.md §5 강제 규칙 2 — 선점 상태 전이는 항상 조건부(WHERE status = :expected)로
     * 실행한다. 영향 행이 1이면 성공, 0이면 이미 다른 경로에서 처리된 것이므로 아무것도 하지 않는다.
     */
    int transitionStatus(Long id, HoldStatus expected, HoldStatus next, LocalDateTime now);
}
