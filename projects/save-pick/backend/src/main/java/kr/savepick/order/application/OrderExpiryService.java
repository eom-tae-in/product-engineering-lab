package kr.savepick.order.application;

import java.time.LocalDateTime;
import kr.savepick.common.audit.ActorType;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.order.domain.Order;
import kr.savepick.order.domain.OrderRepository;
import kr.savepick.order.domain.OrderStatus;
import kr.savepick.order.domain.OrderStatusHistory;
import kr.savepick.order.domain.OrderStatusHistoryRepository;
import kr.savepick.stock.application.InventoryHoldService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PENDING 주문의 선점 만료 종결 — BATCH-01(HoldExpiryReclaimJob), API-018(선점 잔여 조회),
 * API-022(결제, 자기 주문 한정 사전 검사)가 모두 이 서비스 하나만 호출한다(14-project-structure.md
 * L1 — 같은 규칙을 두 벌로 만들지 않는다, 13번 §2). 재고 반영은 항상
 * {@code InventoryHoldService.expireHolds}(HELD → EXPIRED, HOLD_EXPIRE)를 거친다 — 과제 지시로
 * 2단계에서 RELEASED/HOLD_RELEASE로 남기던 1단계의 절충을 바로잡았다.
 *
 * <p>{@code orders} 행을 {@code FOR UPDATE}로 잠근 뒤 조건부 UPDATE로 종결한다(13번 §7.1 락 순서
 * 1번). API-022(결제)는 이 메서드를 별도의(선행) 트랜잭션으로 호출한 뒤에야 자신의 결제 트랜잭션을
 * 시작한다 — 그래야 "만료 종결은 커밋되고 그 뒤에 409를 응답한다"(13번 §4 2단계)를, 결제
 * 트랜잭션 자신이 쥔 락을 다시 잠그려다 자기 자신과 대기하는 상황 없이 만족시킬 수 있다.
 */
@Service
public class OrderExpiryService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final InventoryHoldService inventoryHoldService;

    public OrderExpiryService(
            OrderRepository orderRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            InventoryHoldService inventoryHoldService) {
        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.inventoryHoldService = inventoryHoldService;
    }

    /**
     * API-018·API-022 — 본인 주문 한정. 본인 주문이 아니면 404. 대부분의 호출(아직 만료 전)은
     * 잠그지 않은 채로 끝난다 — API-018은 자주 폴링되는 조회 엔드포인트라 매 호출마다 행 락을
     * 잡으면 결제 트랜잭션과 불필요하게 경합한다. 만료 가능성이 보일 때만 잠그고 다시 확인한다
     * (이중 확인 — 그 사이 다른 트랜잭션이 이미 종결했을 수 있어 잠근 뒤에도 한 번 더 판정한다).
     */
    @Transactional
    public boolean expirePendingOrderIfDue(Long orderId, Long memberId, LocalDateTime now) {
        Order order = orderRepository.findByIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!isDue(order, now)) {
            return false;
        }
        Order locked = orderRepository.findByIdAndMemberIdForUpdate(orderId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return expireIfDue(locked, now);
    }

    /** BATCH-01 — 회원 소유권 검사가 필요 없는 시스템 배치 경로. 주문이 없으면 조용히 건너뛴다. */
    @Transactional
    public boolean expirePendingOrderById(Long orderId, LocalDateTime now) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || !isDue(order, now)) {
            return false;
        }
        Order locked = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (locked == null) {
            return false;
        }
        return expireIfDue(locked, now);
    }

    private boolean isDue(Order order, LocalDateTime now) {
        return order.getStatus() == OrderStatus.PENDING && order.isHoldExpired(now);
    }

    /**
     * {@code order}는 이 트랜잭션 안에서 두 번째로 조회한(FOR UPDATE) 엔티티다 — 같은 영속성
     * 컨텍스트 안에서 같은 ID를 먼저 읽은 적이 있으면(이중 확인의 첫 번째 조회, {@code isDue}
     * 사전 검사), Hibernate 1차 캐시가 SQL은 실제로 다시 실행하고 락도 정말 얻지만, 반환하는
     * 자바 객체는 처음 로드해 둔(잠그기 전) 인스턴스를 그대로 재사용한다 — 그래서 이 시점의
     * {@code order.getStatus()}는 락 대기 중 다른 트랜잭션이 막 커밋한 결과를 반영하지 못할 수
     * 있다(신뢰할 수 없다). 그래서 이 메서드는 자바 쪽 상태값이 아니라 조건부 UPDATE의 영향
     * 행 수만을 최종 판정 근거로 삼는다 — 실제 DB 행을 대상으로 한 {@code WHERE status =
     * 'PENDING'}이 이 트랜잭션 시점의 진짜 상태를 말해준다(14-project-structure.md §9.2).
     */
    private boolean expireIfDue(Order order, LocalDateTime now) {
        if (order.getStatus() != OrderStatus.PENDING || !order.isHoldExpired(now)) {
            return false;
        }
        int affected = orderRepository.expireIfPending(order.getId(), now);
        if (affected != 1) {
            return false;
        }
        inventoryHoldService.expireHolds(order.getId(), ActorType.SYSTEM, null);
        orderStatusHistoryRepository.save(OrderStatusHistory.record(
                order.getId(), OrderStatus.PENDING, OrderStatus.EXPIRED, ActorType.SYSTEM, null, "선점 만료", now));
        return true;
    }
}
