package kr.savepick.account.api;

import static org.assertj.core.api.Assertions.assertThat;

import kr.savepick.common.response.ErrorResponse;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * 12-auth.md §3.1 인가 판정 순서를 실제 HTTP 경로로 검증한다 (14-project-structure.md §10 api/).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class AuthApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("토큰_없이_내_정보를_조회하면_401_UNAUTHENTICATED이다")
    void 토큰_없이_내_정보를_조회하면_401_UNAUTHENTICATED이다() {
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity("/api/me", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    @DisplayName("고객_토큰으로_관리자_경로에_접근하면_403_FORBIDDEN이다")
    void 고객_토큰으로_관리자_경로에_접근하면_403_FORBIDDEN이다() {
        SignUpRequest signUpRequest = new SignUpRequest(
                "apitest1@test.com", "password123", "사용자", "01011112222", null);
        ResponseEntity<SignUpResponse> signUpResponse =
                restTemplate.postForEntity("/api/auth/signup", signUpRequest, SignUpResponse.class);
        assertThat(signUpResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String accessToken = signUpResponse.getBody().accessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/products", HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("관리자_경로에_토큰_없이_접근하면_401이다")
    void 관리자_경로에_토큰_없이_접근하면_401이다() {
        ResponseEntity<ErrorResponse> response =
                restTemplate.getForEntity("/api/admin/products", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("가입한_토큰으로_내_정보를_조회할_수_있다")
    void 가입한_토큰으로_내_정보를_조회할_수_있다() {
        SignUpRequest signUpRequest = new SignUpRequest(
                "apitest2@test.com", "password123", "사용자2", "01011113333", null);
        ResponseEntity<SignUpResponse> signUpResponse =
                restTemplate.postForEntity("/api/auth/signup", signUpRequest, SignUpResponse.class);
        String accessToken = signUpResponse.getBody().accessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<MeResponse> response = restTemplate.exchange(
                "/api/me", HttpMethod.GET, new HttpEntity<>(headers), MeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().email()).isEqualTo("apitest2@test.com");
    }
}
