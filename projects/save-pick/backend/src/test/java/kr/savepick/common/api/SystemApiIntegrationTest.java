package kr.savepick.common.api;

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
 * API-008 (11-api-spec.md §1, FR-005, BR-028). 비로그인 접근 확인 (14-project-structure.md §10 api/).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class SystemApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("토큰_없이도_서버_시각을_조회할_수_있다")
    void 토큰_없이도_서버_시각을_조회할_수_있다() {
        ResponseEntity<SystemController.SystemTimeResponse> response =
                restTemplate.getForEntity("/api/system/time", SystemController.SystemTimeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().serverTime()).isNotNull();
        assertThat(response.getBody().timezone()).isEqualTo("Asia/Seoul");
    }
}
