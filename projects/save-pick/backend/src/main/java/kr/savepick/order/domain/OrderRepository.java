package kr.savepick.order.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    /** 12-auth.md §3.3 소유권 판정 — 본인 주문이 아니면 빈 값(컨트롤러는 404로 번역한다). */
    Optional<Order> findByIdAndMemberId(Long id, Long memberId);

    /** 13번 §7.1 락 순서 1번. 결제·취소·포기처럼 주문 행을 변경하는 흐름에서만 쓴다. */
    Optional<Order> findByIdAndMemberIdForUpdate(Long id, Long memberId);

    /** 관리자 취소·배치처럼 회원 소유권 검사가 필요 없는 흐름에서 쓴다(13번 §7.1 락 순서 1번). */
    Optional<Order> findByIdForUpdate(Long id);

    boolean existsByMemberIdAndStatus(Long memberId, OrderStatus status);

    /** {@code UQ_orders_member_pending}과 짝을 이루는 조회 — 있으면 그 주문의 상세를 오류 응답에 담는다. */
    Optional<Order> findByMemberIdAndStatus(Long memberId, OrderStatus status);

    /**
     * PENDING → EXPIRED 조건부 전이(포기 API-019, 조회·결제 시점 지연 정리 API-018·API-022,
     * BATCH-01 공용). {@code WHERE status = 'PENDING'}으로 실행해 영향 행 수를 확인한다
     * (14-project-structure.md §9.2).
     */
    int expireIfPending(Long id, LocalDateTime now);

    /** BATCH-01 — 대상 조회. {@code IX_orders_hold_expires_at_pending} 부분 인덱스를 탄다. */
    List<Long> findPendingIdsForHoldExpiry(LocalDateTime now, int limit);

    /** BATCH-03 — 대상 조회. {@code IX_orders_no_show_due_at} 부분 인덱스를 탄다. */
    List<Long> findConfirmedOrReadyIdsForNoShow(LocalDateTime now, int limit);

    /** API-022 5단계 — 결제 시도 횟수 증가. 조건 없이 id만으로 실행한다(호출자가 이미 행을 잠갔다). */
    int incrementPaymentAttemptCount(Long id, LocalDateTime now);

    /** API-022 7-성공 — PENDING → CONFIRMED. */
    int confirmPending(Long id, short pickupNumber, LocalDateTime cancelableUntil, LocalDateTime noShowDueAt, LocalDateTime confirmedAt, LocalDateTime now);

    /** API-022 7-실패(3회째) — PENDING → FAILED. {@code stock_settled_at}도 함께 기록한다. */
    int failPending(Long id, LocalDateTime now);

    /** API-115 — CONFIRMED → READY. */
    int transitionToReady(Long id, LocalDateTime now);

    /** API-116 — CONFIRMED·READY → COMPLETED. */
    int transitionToCompleted(Long id, LocalDateTime now);

    /** API-025·117 2단계 — CONFIRMED·READY → CANCELED. */
    int transitionToCanceled(Long id, String canceledBy, String cancelReason, LocalDateTime now);

    /** API-025·117 6단계 — 재고·정원 종결 표시(조회·점검용 보조 신호, 13번 §5). */
    int markStockSettled(Long id, LocalDateTime now);

    /** BATCH-03 — CONFIRMED·READY → NO_SHOW. */
    int transitionToNoShow(Long id, LocalDateTime now);

    /** API-113 픽업 번호로 조회. */
    Optional<Order> findByPickupBusinessDateAndPickupNumber(LocalDate businessDate, short pickupNumber);

    /** API-023 고객 주문 내역. */
    OrderPage findByMemberIdAndStatusIn(Long memberId, List<OrderStatus> statuses, int page, int size);

    /** API-112 관리자 주문 목록. {@code slotId}는 null이면 무시한다. */
    OrderPage findForAdmin(List<LocalDate> pickupDates, List<OrderStatus> statuses, Long slotId, int page, int size);

    record OrderPage(List<Order> items, long totalElements) {
    }
}
