package kr.savepick.stock.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import kr.savepick.common.audit.ActorType;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.stock.domain.HoldStatus;
import kr.savepick.stock.domain.InventoryHold;
import kr.savepick.stock.domain.InventoryHoldRepository;
import kr.savepick.stock.domain.ProductStock;
import kr.savepick.stock.domain.ProductStockRepository;
import kr.savepick.stock.domain.StockChangeReason;
import kr.savepick.stock.domain.StockHoldPolicy;
import kr.savepick.stock.domain.StockQuantities;
import kr.savepick.stock.infrastructure.LockTimeoutGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * stock 도메인의 선점 생성·해제·확정 (14-project-structure.md §4 stock/application).
 * order 도메인이 아직 없어(이번 슬라이스 범위 밖) 이 서비스를 HTTP로 노출하지 않는다 — order가
 * 생기면 {@code OrderDraftService}·{@code PaymentAttemptService}·{@code OrderCancelService}가
 * 이 서비스를 호출한다 (14-project-structure.md §5 표).
 */
@Service
public class InventoryHoldService {

    private final ProductStockRepository productStockRepository;
    private final InventoryHoldRepository inventoryHoldRepository;
    private final ExpiredHoldReclaimer expiredHoldReclaimer;
    private final StockLedgerRecorder stockLedgerRecorder;
    private final LockTimeoutGuard lockTimeoutGuard;
    private final ServerClock serverClock;
    private final Duration holdTtl;

    public InventoryHoldService(
            ProductStockRepository productStockRepository,
            InventoryHoldRepository inventoryHoldRepository,
            ExpiredHoldReclaimer expiredHoldReclaimer,
            StockLedgerRecorder stockLedgerRecorder,
            LockTimeoutGuard lockTimeoutGuard,
            ServerClock serverClock,
            @Value("${savepick.hold.ttl}") String holdTtl) {
        this.productStockRepository = productStockRepository;
        this.inventoryHoldRepository = inventoryHoldRepository;
        this.expiredHoldReclaimer = expiredHoldReclaimer;
        this.stockLedgerRecorder = stockLedgerRecorder;
        this.lockTimeoutGuard = lockTimeoutGuard;
        this.serverClock = serverClock;
        this.holdTtl = Duration.parse(holdTtl);
    }

    /**
     * 13번 §3, §7.1 — {@code product_id} 오름차순으로 잠근 뒤, 전부-또는-전무로 선점을 만든다
     * (BR-027, G7). 하나라도 부족하면 어떤 품목도 선점하지 않고 {@code OUT_OF_STOCK}으로 롤백한다.
     *
     * @param requestedQuantityByProductId 상품ID → 요청 수량 (모두 1 이상)
     */
    @Transactional
    public List<InventoryHold> createHolds(
            Long orderId, Map<Long, Integer> requestedQuantityByProductId, ActorType actorType, Long actorId) {
        lockTimeoutGuard.apply();
        LocalDateTime now = serverClock.now();

        List<Long> sortedProductIds = sortAscending(requestedQuantityByProductId.keySet());
        Map<Long, StockQuantities> lockedQuantities = new LinkedHashMap<>();
        for (Long productId : sortedProductIds) {
            ProductStock stock = productStockRepository.findByProductIdForUpdate(productId).orElse(null);
            StockQuantities quantities = stock == null ? StockQuantities.zero() : StockQuantities.of(stock);
            if (stock != null) {
                quantities = expiredHoldReclaimer.reclaim(productId, quantities, now);
            }
            lockedQuantities.put(productId, quantities);
        }

        List<Shortage> shortages = new ArrayList<>();
        for (Long productId : sortedProductIds) {
            int requested = requestedQuantityByProductId.get(productId);
            StockQuantities quantities = lockedQuantities.get(productId);
            if (!StockHoldPolicy.canHold(quantities, requested)) {
                shortages.add(new Shortage(productId, requested, quantities.available()));
            }
        }
        if (!shortages.isEmpty()) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK, ErrorCode.OUT_OF_STOCK.defaultMessage(), Map.of("shortages", shortages));
        }

        LocalDateTime expiresAt = now.plus(holdTtl);
        List<InventoryHold> holds = new ArrayList<>();
        for (Long productId : sortedProductIds) {
            int requested = requestedQuantityByProductId.get(productId);
            stockLedgerRecorder.record(
                    productId, lockedQuantities.get(productId), StockChangeReason.HOLD,
                    0, requested, 0, 0,
                    orderId, actorType, actorId, null, now);
            InventoryHold hold = InventoryHold.create(orderId, productId, (short) requested, expiresAt, now);
            holds.add(inventoryHoldRepository.save(hold));
        }
        return holds;
    }

    /** 결제 성공(S4) — HELD 선점을 CONFIRMED 판매로 전환한다 (BR-011). */
    @Transactional
    public void confirmHolds(Long orderId, ActorType actorType, Long actorId) {
        transitionOrderHolds(orderId, HoldStatus.CONSUMED, StockChangeReason.CONFIRM, actorType, actorId,
                (quantity) -> new int[] {0, -quantity, quantity, 0});
    }

    /** 결제 3회째 실패 또는 주문서 포기(S5) — HELD 선점을 해제해 판매 가능 재고로 되돌린다 (BR-012). */
    @Transactional
    public void releaseHolds(Long orderId, ActorType actorType, Long actorId) {
        transitionOrderHolds(orderId, HoldStatus.RELEASED, StockChangeReason.HOLD_RELEASE, actorType, actorId,
                (quantity) -> new int[] {0, -quantity, 0, 0});
    }

    /**
     * 선점 유효 시간 경과에 의한 종결(S6) — BATCH-01, API-018·API-022의 "자기 주문 한정" 만료 종결이
     * 모두 이 메서드를 거친다(과제 지시 — 2단계에서 RELEASED/HOLD_RELEASE로 남기던 절충을 바로잡는다).
     * HELD → EXPIRED, 원장 {@code HOLD_EXPIRE}. §2.2의 상품별 지연 정리({@link ExpiredHoldReclaimer})와
     * 재고 수량 변화는 같지만, 주문 전체를 EXPIRED로 종결하는 호출자(주문 행을 이미 잠근 트랜잭션)만
     * 이 메서드를 쓴다 — 상품 행만 잠근 지연 정리 경로는 계속 {@code ExpiredHoldReclaimer}를 쓴다
     * (락 순서, 13번 §7.1).
     */
    @Transactional
    public void expireHolds(Long orderId, ActorType actorType, Long actorId) {
        transitionOrderHolds(orderId, HoldStatus.EXPIRED, StockChangeReason.HOLD_EXPIRE, actorType, actorId,
                (quantity) -> new int[] {0, -quantity, 0, 0});
    }

    private void transitionOrderHolds(
            Long orderId, HoldStatus next, StockChangeReason reason, ActorType actorType, Long actorId,
            java.util.function.IntFunction<int[]> deltasOf) {
        lockTimeoutGuard.apply();
        LocalDateTime now = serverClock.now();

        List<InventoryHold> heldHolds = inventoryHoldRepository.findByOrderIdAndStatus(orderId, HoldStatus.HELD);
        if (heldHolds.isEmpty()) {
            return;
        }

        Map<Long, InventoryHold> holdByProductId = new TreeMap<>();
        for (InventoryHold hold : heldHolds) {
            holdByProductId.put(hold.getProductId(), hold);
        }

        for (Map.Entry<Long, InventoryHold> entry : holdByProductId.entrySet()) {
            Long productId = entry.getKey();
            InventoryHold hold = entry.getValue();
            ProductStock stock = productStockRepository.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> new IllegalStateException("선점이 있는 상품의 재고 행이 없습니다 — productId=" + productId));
            StockQuantities quantities = StockQuantities.of(stock);

            int affected = inventoryHoldRepository.transitionStatus(hold.getId(), HoldStatus.HELD, next, now);
            if (affected != 1) {
                continue;
            }
            int[] deltas = deltasOf.apply(hold.getQuantity());
            stockLedgerRecorder.record(
                    productId, quantities, reason,
                    deltas[0], deltas[1], deltas[2], deltas[3],
                    orderId, actorType, actorId, null, now);
        }
    }

    public Optional<InventoryHold> findHeld(Long orderId, Long productId) {
        return inventoryHoldRepository.findByOrderIdAndStatus(orderId, HoldStatus.HELD).stream()
                .filter(h -> h.getProductId().equals(productId))
                .findFirst();
    }

    /** 13번 §3 — 교착을 막기 위해 product_id 오름차순으로 잠근다. */
    static List<Long> sortAscending(java.util.Set<Long> productIds) {
        return productIds.stream().sorted().toList();
    }

    public record Shortage(Long productId, int requested, int available) {
    }
}
