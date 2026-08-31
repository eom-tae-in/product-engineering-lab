package kr.savepick.order.application;

import java.time.LocalDateTime;
import kr.savepick.account.application.OrderRestrictionService;
import kr.savepick.common.audit.ActorType;
import kr.savepick.order.domain.Order;
import kr.savepick.order.domain.OrderStatus;
import kr.savepick.order.domain.OrderStatusHistory;
import kr.savepick.order.domain.OrderStatusHistoryRepository;
import kr.savepick.order.domain.OrderRepository;
import kr.savepick.pickup.application.PickupSlotReserveService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BATCH-03 노쇼 자동 전환·누적 제재 (11-api-spec.md §11, BR-021~023, 05-state-rules.md §8).
 * 주문 1건 = 트랜잭션 1개(14-project-structure.md §6.2). 재고는 바꾸지 않는다(BR-022, S10) —
 * 정원만 반납한다. 05 §8 전이표가 CONFIRMED/READY → NO_SHOW를 "-1(반납)"로 명시하므로,
 * BR-016이 정원 소진 판정 대상에서 NO_SHOW를 제외한 것과 일치시키기 위해 슬롯을 반납한다.
 *
 * <p>제재 생성은 {@code account/application/OrderRestrictionService}를 호출한다
 * (14-project-structure.md §6.1 "제재 생성은 account 서비스를 호출한다").
 */
@Service
public class NoShowService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PickupSlotReserveService pickupSlotReserveService;
    private final OrderRestrictionService orderRestrictionService;

    public NoShowService(
            OrderRepository orderRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            PickupSlotReserveService pickupSlotReserveService,
            OrderRestrictionService orderRestrictionService) {
        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.pickupSlotReserveService = pickupSlotReserveService;
        this.orderRestrictionService = orderRestrictionService;
    }

    /** @return 실제로 전환했으면 true(영향 0이면 이미 처리된 것이므로 false). */
    @Transactional
    public boolean convertToNoShow(Long orderId, LocalDateTime now) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null) {
            return false;
        }
        OrderStatus fromStatus = order.getStatus();
        if (fromStatus != OrderStatus.CONFIRMED && fromStatus != OrderStatus.READY) {
            return false;
        }
        int affected = orderRepository.transitionToNoShow(orderId, now);
        if (affected != 1) {
            return false;
        }

        if (order.getPickupSlotId() != null) {
            pickupSlotReserveService.release(order.getPickupSlotId());
        }
        orderStatusHistoryRepository.save(OrderStatusHistory.record(
                orderId, fromStatus, OrderStatus.NO_SHOW, ActorType.SYSTEM, null, "픽업 유예 경과", now));

        orderRestrictionService.applyNoShowRestriction(order.getMemberId(), orderId, now);
        return true;
    }
}
