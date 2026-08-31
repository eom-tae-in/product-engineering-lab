package kr.savepick.stock.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 13번 §3, §7.1 — 다품목 선점은 교착을 막기 위해 product_id 오름차순으로 잠근다.
 * DB 없이 정렬 로직만 단위 테스트로 검증한다 (docs/13-inventory-concurrency.md §7.1).
 */
class InventoryHoldServiceLockOrderTest {

    @Test
    @DisplayName("여러_상품ID를_입력_순서와_무관하게_오름차순으로_정렬한다")
    void 여러_상품ID를_입력_순서와_무관하게_오름차순으로_정렬한다() {
        Set<Long> productIds = new LinkedHashSet<>(java.util.List.of(30L, 10L, 20L, 5L));

        var sorted = InventoryHoldService.sortAscending(productIds);

        assertThat(sorted).containsExactly(5L, 10L, 20L, 30L);
    }

    @Test
    @DisplayName("이미_정렬된_상태로_들어와도_결과는_동일하다")
    void 이미_정렬된_상태로_들어와도_결과는_동일하다() {
        Set<Long> productIds = new LinkedHashSet<>(java.util.List.of(1L, 2L, 3L));

        var sorted = InventoryHoldService.sortAscending(productIds);

        assertThat(sorted).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("단일_상품이면_그대로_1건이다")
    void 단일_상품이면_그대로_1건이다() {
        Set<Long> productIds = Set.of(42L);

        var sorted = InventoryHoldService.sortAscending(productIds);

        assertThat(sorted).containsExactly(42L);
    }
}
