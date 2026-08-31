package kr.savepick.stock.application;

import java.time.LocalDateTime;
import java.util.List;
import kr.savepick.common.audit.ActorType;
import kr.savepick.stock.domain.HoldStatus;
import kr.savepick.stock.domain.InventoryHold;
import kr.savepick.stock.domain.InventoryHoldRepository;
import kr.savepick.stock.domain.StockChangeReason;
import kr.savepick.stock.domain.StockQuantities;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 13번 §2.2 (b) 쓰기 지연 정리 — {@code product_stocks} 행을 이미 {@code FOR UPDATE}로 잠근
 * 트랜잭션 안에서만 호출한다. 새 트랜잭션을 시작하지 않고 호출자의 트랜잭션에 참여하며,
 * 잠근 상품의 만료 선점만 회수한다. 그 선점이 속한 주문의 {@code orders} 행은 건드리지 않는다
 * (락 순서를 지키기 위함, 14-project-structure.md §5, 13번 §7.1).
 */
@Service
public class ExpiredHoldReclaimer {

    private final InventoryHoldRepository inventoryHoldRepository;
    private final StockLedgerRecorder stockLedgerRecorder;

    public ExpiredHoldReclaimer(InventoryHoldRepository inventoryHoldRepository, StockLedgerRecorder stockLedgerRecorder) {
        this.inventoryHoldRepository = inventoryHoldRepository;
        this.stockLedgerRecorder = stockLedgerRecorder;
    }

    /**
     * @param productId 이미 잠근 상품
     * @param current    잠근 시점(또는 그 이후 최신) 수량 값
     * @param now        판정 기준 시각
     * @return 회수를 반영한 최신 수량 값. 회수 대상이 없으면 {@code current}를 그대로 돌려준다.
     */
    @Transactional
    public StockQuantities reclaim(Long productId, StockQuantities current, LocalDateTime now) {
        List<InventoryHold> expired =
                inventoryHoldRepository.findByProductIdAndStatusAndExpiresAtLessThanEqual(productId, HoldStatus.HELD, now);

        StockQuantities quantities = current;
        for (InventoryHold hold : expired) {
            int affected = inventoryHoldRepository.transitionStatus(hold.getId(), HoldStatus.HELD, HoldStatus.EXPIRED, now);
            if (affected != 1) {
                // 다른 트랜잭션이 이미 이 선점을 처리했다 (배치·다른 요청과의 경합). 원장 중복 기록을 만들지 않는다.
                continue;
            }
            quantities = stockLedgerRecorder.record(
                    productId, quantities, StockChangeReason.HOLD_EXPIRE,
                    0, -hold.getQuantity(), 0, 0,
                    hold.getOrderId(), ActorType.SYSTEM, null, null, now);
        }
        return quantities;
    }
}
