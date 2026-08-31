package kr.savepick.pickup.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import kr.savepick.pickup.domain.SlotSelectablePolicy.Evaluation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BR-013·015·016·017 (docs/16-test-plan.md TC-037~044). DB 없이 정책만 검증한다
 * (14-project-structure.md §10 domain/).
 */
class SlotSelectablePolicyTest {

    private static final Duration RESERVATION_CLOSE_BEFORE = Duration.ofMinutes(30);
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 28);

    @Test
    @DisplayName("TC_037_오늘과_내일만_선택_가능하고_D_2는_불가하다")
    void TC_037_오늘과_내일만_선택_가능하고_D_2는_불가하다() {
        assertThat(SlotSelectablePolicy.isDateSelectable(TODAY, TODAY, 2)).isTrue();
        assertThat(SlotSelectablePolicy.isDateSelectable(TODAY.plusDays(1), TODAY, 2)).isTrue();
        assertThat(SlotSelectablePolicy.isDateSelectable(TODAY.plusDays(2), TODAY, 2)).isFalse();
        assertThat(SlotSelectablePolicy.isDateSelectable(TODAY.minusDays(1), TODAY, 2)).isFalse();
    }

    @Test
    @DisplayName("TC_041_예약_마감_시각을_지나면_선택할_수_없다")
    void TC_041_예약_마감_시각을_지나면_선택할_수_없다() {
        // 19:00~19:30 시간대의 예약 마감은 18:30이다.
        PickupSlot slot = slotAt(19, 0);
        LocalDateTime now = TODAY.atTime(19, 31);

        Evaluation evaluation = SlotSelectablePolicy.evaluate(slot, now, null, RESERVATION_CLOSE_BEFORE);

        assertThat(evaluation.selectable()).isFalse();
        assertThat(evaluation.reason()).isEqualTo(UnselectableReason.RESERVATION_CLOSED);
    }

    @Test
    @DisplayName("예약_마감_시각_정각에는_아직_선택_가능하다")
    void 예약_마감_시각_정각에는_아직_선택_가능하다() {
        PickupSlot slot = slotAt(20, 0);
        LocalDateTime deadline = TODAY.atTime(19, 30);

        Evaluation evaluation = SlotSelectablePolicy.evaluate(slot, deadline, null, RESERVATION_CLOSE_BEFORE);

        assertThat(evaluation.selectable()).isTrue();
    }

    @Test
    @DisplayName("TC_042_정원에_도달하면_선택할_수_없다")
    void TC_042_정원에_도달하면_선택할_수_없다() {
        PickupSlot slot = slotWithCapacity(2, 2);
        // 슬롯 시작(10:00)의 예약 마감(9:30) 이전 시각이어야 SLOT_FULL 판정을 관찰할 수 있다.
        LocalDateTime now = TODAY.atTime(9, 0);

        Evaluation evaluation = SlotSelectablePolicy.evaluate(slot, now, null, RESERVATION_CLOSE_BEFORE);

        assertThat(evaluation.selectable()).isFalse();
        assertThat(evaluation.reason()).isEqualTo(UnselectableReason.SLOT_FULL);
    }

    @Test
    @DisplayName("TC_043_상품_최이른_마감_시각_이후_시작하면_선택할_수_없다")
    void TC_043_상품_최이른_마감_시각_이후_시작하면_선택할_수_없다() {
        PickupSlot slot = slotAt(19, 30);
        LocalDateTime now = TODAY.atTime(10, 0);
        LocalDateTime earliestClosingAt = TODAY.atTime(19, 0);

        Evaluation evaluation = SlotSelectablePolicy.evaluate(slot, now, earliestClosingAt, RESERVATION_CLOSE_BEFORE);

        assertThat(evaluation.selectable()).isFalse();
        assertThat(evaluation.reason()).isEqualTo(UnselectableReason.AFTER_PRODUCT_CLOSING);
    }

    @Test
    @DisplayName("TC_044_관리자가_차단한_시간대는_선택할_수_없다")
    void TC_044_관리자가_차단한_시간대는_선택할_수_없다() {
        PickupSlot slot = slotAt(19, 30);
        slot.block();
        LocalDateTime now = TODAY.atTime(10, 0);

        Evaluation evaluation = SlotSelectablePolicy.evaluate(slot, now, null, RESERVATION_CLOSE_BEFORE);

        assertThat(evaluation.selectable()).isFalse();
        assertThat(evaluation.reason()).isEqualTo(UnselectableReason.BLOCKED);

        slot.unblock();
        Evaluation afterUnblock = SlotSelectablePolicy.evaluate(slot, now, null, RESERVATION_CLOSE_BEFORE);
        assertThat(afterUnblock.selectable()).isTrue();
    }

    @Test
    @DisplayName("아무_제약도_없으면_선택_가능하다")
    void 아무_제약도_없으면_선택_가능하다() {
        PickupSlot slot = slotAt(19, 30);
        LocalDateTime now = TODAY.atTime(10, 0);
        LocalDateTime earliestClosingAt = TODAY.atTime(21, 0);

        Evaluation evaluation = SlotSelectablePolicy.evaluate(slot, now, earliestClosingAt, RESERVATION_CLOSE_BEFORE);

        assertThat(evaluation.selectable()).isTrue();
        assertThat(evaluation.reason()).isNull();
    }

    private PickupSlot slotAt(int hour, int minute) {
        LocalDateTime start = TODAY.atTime(hour, minute);
        return PickupSlot.create((short) 1, TODAY, start, start.plusMinutes(30), (short) 20, TODAY.atStartOfDay());
    }

    private PickupSlot slotWithCapacity(int capacity, int reservedCount) {
        PickupSlot slot = PickupSlot.create(
                (short) 1, TODAY, TODAY.atTime(10, 0), TODAY.atTime(10, 30), (short) capacity, TODAY.atStartOfDay());
        for (int i = 0; i < reservedCount; i++) {
            incrementReservedCountForTest(slot);
        }
        return slot;
    }

    /** 테스트 전용 — 정원 도달 상황을 만들기 위해 리플렉션으로 reservedCount를 올린다(엔티티는 세터를 두지 않는다). */
    private void incrementReservedCountForTest(PickupSlot slot) {
        try {
            var field = PickupSlot.class.getDeclaredField("reservedCount");
            field.setAccessible(true);
            short current = (short) field.get(slot);
            field.set(slot, (short) (current + 1));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
