package kr.savepick.store.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import kr.savepick.store.domain.Store;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** API-009, API-120 (11-api-spec.md §1, §10). V2__seed_store.sql로 심어둔 초기 행을 읽는다. */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class StoreQueryServiceIntegrationTest {

    @Autowired
    private StoreQueryService storeQueryService;

    @Test
    @DisplayName("시드된_매장_정보를_조회할_수_있다")
    void 시드된_매장_정보를_조회할_수_있다() {
        Store store = storeQueryService.getStore();

        assertThat(store.getName()).isEqualTo("savePick 신선마켓");
        assertThat(store.getAddress()).isEqualTo("서울특별시 ○○구 ○○로 12");
        assertThat(store.getPhone()).isEqualTo("0212345678");
        assertThat(store.getSlotUnitMinutes()).isEqualTo((short) 30);
    }

    @Test
    @DisplayName("매장_운영_설정_조회에는_휴무일_목록이_포함된다")
    void 매장_운영_설정_조회에는_휴무일_목록이_포함된다() {
        StoreQueryService.StoreSettings settings = storeQueryService.getStoreSettings();

        assertThat(settings.store().getOpenTime()).isInstanceOf(LocalTime.class);
        assertThat(settings.holidays()).isNotNull();
    }
}
