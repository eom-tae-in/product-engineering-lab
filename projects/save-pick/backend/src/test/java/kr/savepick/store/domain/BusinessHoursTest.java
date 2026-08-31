package kr.savepick.store.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BR-014. DB 없이 정책 자체만 검증한다 (14-project-structure.md §10 domain/).
 */
class BusinessHoursTest {

    @Test
    @DisplayName("시작이_종료보다_이르고_30분_단위면_유효하다")
    void 시작이_종료보다_이르고_30분_단위면_유효하다() {
        assertThat(BusinessHours.isValid(LocalTime.of(10, 0), LocalTime.of(22, 0))).isTrue();
        assertThat(BusinessHours.isValid(LocalTime.of(9, 30), LocalTime.of(21, 30))).isTrue();
    }

    @Test
    @DisplayName("종료가_시작보다_같거나_이르면_무효하다")
    void 종료가_시작보다_같거나_이르면_무효하다() {
        assertThat(BusinessHours.isValid(LocalTime.of(10, 0), LocalTime.of(10, 0))).isFalse();
        assertThat(BusinessHours.isValid(LocalTime.of(22, 0), LocalTime.of(10, 0))).isFalse();
    }

    @Test
    @DisplayName("30분_단위가_아니면_무효하다")
    void 삼십분_단위가_아니면_무효하다() {
        assertThat(BusinessHours.isValid(LocalTime.of(10, 15), LocalTime.of(22, 0))).isFalse();
        assertThat(BusinessHours.isValid(LocalTime.of(10, 0), LocalTime.of(21, 45))).isFalse();
    }

    @Test
    @DisplayName("null이면_무효하다")
    void null이면_무효하다() {
        assertThat(BusinessHours.isValid(null, LocalTime.of(22, 0))).isFalse();
        assertThat(BusinessHours.isValid(LocalTime.of(10, 0), null)).isFalse();
    }
}
