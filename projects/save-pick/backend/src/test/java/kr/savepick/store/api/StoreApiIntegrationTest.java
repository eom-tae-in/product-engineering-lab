package kr.savepick.store.api;

import static org.assertj.core.api.Assertions.assertThat;

import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * API-009 (11-api-spec.md §1, FR-033). 비로그인 접근 확인 (14-project-structure.md §10 api/).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class StoreApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("토큰_없이도_매장_정보를_조회할_수_있다")
    void 토큰_없이도_매장_정보를_조회할_수_있다() {
        ResponseEntity<StoreResponse> response = restTemplate.getForEntity("/api/store", StoreResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("savePick 신선마켓");
        assertThat(response.getBody().address()).isEqualTo("서울특별시 ○○구 ○○로 12");
        assertThat(response.getBody().phone()).isEqualTo("0212345678");
        assertThat(response.getBody().slotUnitMinutes()).isEqualTo(30);
    }
}
