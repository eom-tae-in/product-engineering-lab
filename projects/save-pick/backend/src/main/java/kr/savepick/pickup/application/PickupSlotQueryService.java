package kr.savepick.pickup.application;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.pickup.domain.PickupSlot;
import kr.savepick.pickup.domain.PickupSlotRepository;
import kr.savepick.pickup.domain.SlotSelectablePolicy;
import kr.savepick.pickup.domain.SlotSelectablePolicy.Evaluation;
import kr.savepick.pickup.domain.UnselectableReason;
import kr.savepick.pickup.infrastructure.PickupOrderReadDao;
import kr.savepick.store.application.StoreQueryService;
import kr.savepick.store.domain.Store;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-020 선택 가능 시간대 조회, API-118 관리자 시간대 현황 조회 (BR-013·014·015·016·017).
 * {@code order} 도메인은 이 서비스를 통해서만 pickup 데이터를 읽는다(14-project-structure.md §4.1).
 */
@Service
public class PickupSlotQueryService {

    private final PickupSlotRepository pickupSlotRepository;
    private final PickupOrderReadDao pickupOrderReadDao;
    private final StoreQueryService storeQueryService;
    private final int selectableDays;
    private final Duration reservationCloseBefore;

    public PickupSlotQueryService(
            PickupSlotRepository pickupSlotRepository,
            PickupOrderReadDao pickupOrderReadDao,
            StoreQueryService storeQueryService,
            @Value("${savepick.pickup.selectable-days}") int selectableDays,
            @Value("${savepick.pickup.reservation-close-before}") String reservationCloseBefore) {
        this.pickupSlotRepository = pickupSlotRepository;
        this.pickupOrderReadDao = pickupOrderReadDao;
        this.storeQueryService = storeQueryService;
        this.selectableDays = selectableDays;
        this.reservationCloseBefore = Duration.parse(reservationCloseBefore);
    }

    /** API-020. {@code requestedDate}가 null이면 선택 가능한 모든 날짜(D+0~D+1)의 시간대를 담는다. */
    @Transactional(readOnly = true)
    public SelectableSlotsResult getSelectableSlots(LocalDate requestedDate, LocalDateTime earliestClosingAt, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        if (requestedDate != null && !SlotSelectablePolicy.isDateSelectable(requestedDate, today, selectableDays)) {
            throw new BusinessException(ErrorCode.SLOT_DATE_OUT_OF_RANGE);
        }
        List<LocalDate> holidays = storeQueryService.getStoreSettings().holidays();

        List<SelectableDate> selectableDates = new ArrayList<>();
        List<SlotView> slotViews = new ArrayList<>();
        for (int offset = 0; offset < selectableDays; offset++) {
            LocalDate date = today.plusDays(offset);
            boolean holiday = holidays.contains(date);
            List<PickupSlot> slots = pickupSlotRepository.findByStoreIdAndSlotDateOrderByStartAtAsc(Store.SINGLETON_ID, date);

            List<Evaluation> evaluations = new ArrayList<>();
            for (PickupSlot slot : slots) {
                evaluations.add(holiday
                        ? Evaluation.unselectable(UnselectableReason.HOLIDAY)
                        : SlotSelectablePolicy.evaluate(slot, now, earliestClosingAt, reservationCloseBefore));
            }

            boolean dateSelectable = !holiday && evaluations.stream().anyMatch(Evaluation::selectable);
            UnselectableReason dateReason = dateSelectable
                    ? null
                    : holiday ? UnselectableReason.HOLIDAY : evaluations.isEmpty() ? null : evaluations.get(0).reason();
            selectableDates.add(new SelectableDate(date, labelFor(offset), dateSelectable, dateReason));

            if (requestedDate == null || requestedDate.equals(date)) {
                for (int i = 0; i < slots.size(); i++) {
                    PickupSlot slot = slots.get(i);
                    Evaluation evaluation = evaluations.get(i);
                    slotViews.add(new SlotView(
                            slot.getId(), date, slot.getStartAt(), slot.getEndAt(), slot.getCapacity(), slot.getReservedCount(),
                            evaluation.selectable(), evaluation.reason()));
                }
            }
        }
        return new SelectableSlotsResult(now, selectableDates, slotViews);
    }

    /**
     * API-021 지정 대상 시간대를 검증한다. 오류 코드 매핑(BLOCKED·RESERVATION_CLOSED → SLOT_CLOSED,
     * SLOT_FULL → SLOT_FULL, AFTER_PRODUCT_CLOSING → SLOT_AFTER_PRODUCT_CLOSING)은 이 서비스가
     * 맡는다 — SLOT_* 오류 코드의 의미는 pickup 도메인 소유다.
     */
    @Transactional(readOnly = true)
    public SlotAssignment validateForAssignment(Long slotId, LocalDateTime earliestClosingAt, LocalDateTime now) {
        PickupSlot slot = pickupSlotRepository.findById(slotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));

        LocalDate today = now.toLocalDate();
        if (!SlotSelectablePolicy.isDateSelectable(slot.getSlotDate(), today, selectableDays)) {
            throw new BusinessException(ErrorCode.SLOT_DATE_OUT_OF_RANGE);
        }
        if (storeQueryService.getStoreSettings().holidays().contains(slot.getSlotDate())) {
            throw new BusinessException(ErrorCode.SLOT_DATE_OUT_OF_RANGE);
        }

        Evaluation evaluation = SlotSelectablePolicy.evaluate(slot, now, earliestClosingAt, reservationCloseBefore);
        if (!evaluation.selectable()) {
            throw switch (evaluation.reason()) {
                case BLOCKED, RESERVATION_CLOSED -> new BusinessException(ErrorCode.SLOT_CLOSED);
                case SLOT_FULL -> new BusinessException(ErrorCode.SLOT_FULL);
                case AFTER_PRODUCT_CLOSING -> new BusinessException(ErrorCode.SLOT_AFTER_PRODUCT_CLOSING);
                case HOLIDAY -> new BusinessException(ErrorCode.SLOT_DATE_OUT_OF_RANGE);
            };
        }
        return new SlotAssignment(slot.getId(), slot.getSlotDate(), slot.getStartAt(), slot.getEndAt());
    }

    /** API-118. */
    @Transactional(readOnly = true)
    public AdminSlotOverview getAdminOverview(LocalDate date, LocalDateTime now) {
        boolean holiday = storeQueryService.getStoreSettings().holidays().contains(date);
        List<PickupSlot> slots = pickupSlotRepository.findByStoreIdAndSlotDateOrderByStartAtAsc(Store.SINGLETON_ID, date);

        List<AdminSlotItem> items = new ArrayList<>();
        for (PickupSlot slot : slots) {
            boolean reservationClosed = now.isAfter(slot.getStartAt().minus(reservationCloseBefore));
            List<PickupOrderReadDao.ItemTotalRow> itemTotals = pickupOrderReadDao.sumItemsBySlotId(slot.getId());
            items.add(new AdminSlotItem(
                    slot.getId(), slot.getStartAt(), slot.getEndAt(), slot.getCapacity(), slot.getReservedCount(),
                    slot.isFull(), slot.isBlocked(), reservationClosed, itemTotals));
        }
        return new AdminSlotOverview(date, holiday, items);
    }

    private String labelFor(int offset) {
        return "D+" + offset;
    }

    public record SelectableDate(LocalDate date, String label, boolean selectable, UnselectableReason unselectableReason) {
    }

    public record SlotView(
            Long slotId, LocalDate date, LocalDateTime startAt, LocalDateTime endAt, short capacity, short reservedCount,
            boolean selectable, UnselectableReason unselectableReason) {
    }

    public record SelectableSlotsResult(LocalDateTime serverTime, List<SelectableDate> selectableDates, List<SlotView> slots) {
    }

    public record SlotAssignment(Long slotId, LocalDate slotDate, LocalDateTime startAt, LocalDateTime endAt) {
    }

    public record AdminSlotItem(
            Long slotId, LocalDateTime startAt, LocalDateTime endAt, short capacity, short reservedCount,
            boolean full, boolean blocked, boolean reservationClosed, List<PickupOrderReadDao.ItemTotalRow> itemTotals) {
    }

    public record AdminSlotOverview(LocalDate date, boolean isHoliday, List<AdminSlotItem> slots) {
    }
}
