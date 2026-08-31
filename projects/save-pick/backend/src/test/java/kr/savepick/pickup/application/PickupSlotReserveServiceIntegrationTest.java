package kr.savepick.pickup.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.pickup.domain.PickupSlot;
import kr.savepick.pickup.domain.PickupSlotRepository;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 13번 §8 — 정원 점유·반납. order 도메인의 결제·취소 서비스(2단계)가 아직 없어(과제 지시)
 * 이 슬라이스에서는 이 서비스를 직접 호출해 검증한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class PickupSlotReserveServiceIntegrationTest {

    @Autowired
    private PickupSlotReserveService pickupSlotReserveService;

    @Autowired
    private PickupSlotRepository pickupSlotRepository;

    @Autowired
    private ServerClock serverClock;

    /**
     * 이 테스트는 {@code PickupSlotReserveService.occupy/release}만 직접 검증하므로 선택
     * 가능 여부(D+0~D+1, BR-013)와는 무관하다 — 표준 시간대 그리드(BATCH-05, D+0·D+1의
     * 10:00~22:00)와 겹치지 않도록 충분히 먼 날짜를 쓴다. 다른(트랜잭션 없는) 동시성 테스트가
     * 실제 주문 흐름으로 D+0·D+1 슬롯을 만들어 두더라도 {@code UNIQUE(store_id, start_at)}와
     * 부딪히지 않는다.
     */
    private PickupSlot createSlot(short capacity) {
        LocalDateTime now = serverClock.now();
        LocalDate date = now.toLocalDate().plusDays(5);
        LocalDateTime start = date.atTime(20, 0);
        PickupSlot slot = PickupSlot.create((short) 1, date, start, start.plusMinutes(30), capacity, now);
        return pickupSlotRepository.save(slot);
    }

    @Test
    @DisplayName("점유하면_정원이_1_늘어난다")
    void 점유하면_정원이_1_늘어난다() {
        PickupSlot slot = createSlot((short) 2);

        PickupSlot occupied = pickupSlotReserveService.occupy(slot.getId());

        assertThat(occupied.getReservedCount()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("정원이_가득_차면_SLOT_FULL을_던진다")
    void 정원이_가득_차면_SLOT_FULL을_던진다() {
        PickupSlot slot = createSlot((short) 1);
        pickupSlotReserveService.occupy(slot.getId());

        assertThatThrownBy(() -> pickupSlotReserveService.occupy(slot.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.SLOT_FULL);
    }

    @Test
    @DisplayName("반납하면_정원이_1_줄어들고_0_미만으로_내려가지_않는다")
    void 반납하면_정원이_1_줄어들고_0_미만으로_내려가지_않는다() {
        PickupSlot slot = createSlot((short) 2);
        pickupSlotReserveService.occupy(slot.getId());

        PickupSlot released = pickupSlotReserveService.release(slot.getId());
        assertThat(released.getReservedCount()).isEqualTo((short) 0);

        PickupSlot releasedAgain = pickupSlotReserveService.release(slot.getId());
        assertThat(releasedAgain.getReservedCount()).isEqualTo((short) 0);
    }
}
