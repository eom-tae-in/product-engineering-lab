package kr.savepick.store.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.account.domain.Role;
import kr.savepick.account.infrastructure.BcryptPasswordHasher;
import kr.savepick.account.infrastructure.JwtAccessTokenIssuer;
import kr.savepick.common.response.ErrorResponse;
import kr.savepick.common.time.ServerClock;
import kr.savepick.store.application.StoreSettingService;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * API-120·API-121 (11-api-spec.md §10, 12-auth.md §3.1). 권한 판정과 BR-014 영업시간 규칙을
 * 실제 HTTP 경로로 검증한다 (14-project-structure.md §10 api/).
 * stores는 단일 행(id=1)이라 다른 테스트 클래스의 상태와 겹치지 않도록 매 테스트 전후로 기본값으로 되돌린다.
 * pickup_slots도 마찬가지다 — 2단계부터 excludedFutureSlotCount·keptConfirmedOrderCount가
 * 실제로 pickup_slots를 읽으므로(StoreSettingImpactReadDao), 다른 테스트 클래스가 미리 만들어
 * 둔 슬롯이 섞이지 않도록 오늘·내일 슬롯을 매 테스트 전에 지운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class AdminStoreSettingApiIntegrationTest {

    private static final LocalTime DEFAULT_OPEN = LocalTime.of(10, 0);
    private static final LocalTime DEFAULT_CLOSE = LocalTime.of(22, 0);
    private static final short DEFAULT_CAPACITY = 20;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BcryptPasswordHasher passwordHasher;

    @Autowired
    private JwtAccessTokenIssuer tokenIssuer;

    @Autowired
    private ServerClock serverClock;

    @Autowired
    private StoreSettingService storeSettingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetStoreSettingsBeforeEach() {
        // orders.pickup_slot_id가 참조 중인 슬롯은 FK 때문에 지울 수 없다 — 다른 테스트 클래스가
        // 이미 주문에 연결해 둔 슬롯은 그대로 남기고, 아직 아무 주문도 참조하지 않는 슬롯만 지운다.
        jdbcTemplate.update(
                "DELETE FROM pickup_slots WHERE slot_date IN (CURRENT_DATE, CURRENT_DATE + 1) "
                        + "AND id NOT IN (SELECT pickup_slot_id FROM orders WHERE pickup_slot_id IS NOT NULL)");
        resetStoreSettings();
    }

    @AfterEach
    void resetStoreSettingsAfterEach() {
        resetStoreSettings();
    }

    private void resetStoreSettings() {
        storeSettingService.updateSettings(DEFAULT_OPEN, DEFAULT_CLOSE, DEFAULT_CAPACITY, List.of());
    }

    private String issueAdminToken() {
        Member admin = Member.registerAdmin(
                "admin-" + UUID.randomUUID() + "@test.com",
                passwordHasher.hash("adminpass1"),
                "관리자",
                "01099998888",
                serverClock.now());
        admin = memberRepository.save(admin);
        return tokenIssuer.issue(admin.getId(), Role.ADMIN, UUID.randomUUID(), serverClock.now()).token();
    }

    private String issueCustomerToken() {
        Member customer = Member.registerCustomer(
                "customer-" + UUID.randomUUID() + "@test.com",
                passwordHasher.hash("password123"),
                "고객",
                "01011112222",
                serverClock.now());
        customer = memberRepository.save(customer);
        return tokenIssuer.issue(customer.getId(), Role.CUSTOMER, UUID.randomUUID(), serverClock.now()).token();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    @DisplayName("토큰_없이_설정을_조회하면_401이다")
    void 토큰_없이_설정을_조회하면_401이다() {
        ResponseEntity<ErrorResponse> response =
                restTemplate.getForEntity("/api/admin/store-settings", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("고객_토큰으로_설정을_조회하면_403이다")
    void 고객_토큰으로_설정을_조회하면_403이다() {
        String customerToken = issueCustomerToken();

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/store-settings", HttpMethod.GET,
                new HttpEntity<>(bearer(customerToken)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("고객_토큰으로_설정을_변경하면_403이다")
    void 고객_토큰으로_설정을_변경하면_403이다() {
        String customerToken = issueCustomerToken();
        UpdateStoreSettingsRequest request =
                new UpdateStoreSettingsRequest(DEFAULT_OPEN, LocalTime.of(21, 0), 10, List.of());

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/store-settings", HttpMethod.PUT,
                new HttpEntity<>(request, bearer(customerToken)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("관리자_토큰으로_설정을_조회할_수_있다")
    void 관리자_토큰으로_설정을_조회할_수_있다() {
        String adminToken = issueAdminToken();

        ResponseEntity<StoreSettingsResponse> response = restTemplate.exchange(
                "/api/admin/store-settings", HttpMethod.GET,
                new HttpEntity<>(bearer(adminToken)), StoreSettingsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().openTime()).isEqualTo(DEFAULT_OPEN);
        assertThat(response.getBody().closeTime()).isEqualTo(DEFAULT_CLOSE);
        assertThat(response.getBody().defaultSlotCapacity()).isEqualTo(DEFAULT_CAPACITY);
        assertThat(response.getBody().holidays()).isEmpty();
    }

    @Test
    @DisplayName("TC_101_영업시간_역전이면_BUSINESS_HOUR_INVALID이다")
    void TC_101_영업시간_역전이면_BUSINESS_HOUR_INVALID이다() {
        String adminToken = issueAdminToken();
        UpdateStoreSettingsRequest request =
                new UpdateStoreSettingsRequest(LocalTime.of(22, 0), LocalTime.of(10, 0), 20, List.of());

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/store-settings", HttpMethod.PUT,
                new HttpEntity<>(request, bearer(adminToken)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("BUSINESS_HOUR_INVALID");
    }

    @Test
    @DisplayName("30분_단위가_아니면_BUSINESS_HOUR_INVALID이다")
    void 삼십분_단위가_아니면_BUSINESS_HOUR_INVALID이다() {
        String adminToken = issueAdminToken();
        UpdateStoreSettingsRequest request =
                new UpdateStoreSettingsRequest(LocalTime.of(10, 10), LocalTime.of(21, 0), 20, List.of());

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/store-settings", HttpMethod.PUT,
                new HttpEntity<>(request, bearer(adminToken)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("BUSINESS_HOUR_INVALID");
    }

    @Test
    @DisplayName("정원이_1미만이면_VALIDATION_ERROR이다")
    void 정원이_1미만이면_VALIDATION_ERROR이다() {
        String adminToken = issueAdminToken();
        UpdateStoreSettingsRequest request =
                new UpdateStoreSettingsRequest(DEFAULT_OPEN, DEFAULT_CLOSE, 0, List.of());

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/store-settings", HttpMethod.PUT,
                new HttpEntity<>(request, bearer(adminToken)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("정상적인_설정_변경_후_재조회하면_값이_반영돼_있다")
    void 정상적인_설정_변경_후_재조회하면_값이_반영돼_있다() {
        String adminToken = issueAdminToken();
        UpdateStoreSettingsRequest request = new UpdateStoreSettingsRequest(
                LocalTime.of(10, 0), LocalTime.of(21, 0), 12, List.of(LocalDate.of(2026, 9, 1)));

        ResponseEntity<UpdateStoreSettingsResponse> putResponse = restTemplate.exchange(
                "/api/admin/store-settings", HttpMethod.PUT,
                new HttpEntity<>(request, bearer(adminToken)), UpdateStoreSettingsResponse.class);

        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(putResponse.getBody()).isNotNull();
        assertThat(putResponse.getBody().openTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(putResponse.getBody().closeTime()).isEqualTo(LocalTime.of(21, 0));
        assertThat(putResponse.getBody().defaultSlotCapacity()).isEqualTo(12);
        assertThat(putResponse.getBody().holidays()).containsExactly(LocalDate.of(2026, 9, 1));
        // 이 테스트 클래스는 오늘·내일의 "아직 주문에 쓰이지 않은" 슬롯만 정리한다(FK 때문에
        // 주문이 참조 중인 슬롯은 지울 수 없다) — 실제 계산 자체는
        // StoreSettingServiceIntegrationTest(트랜잭션 롤백으로 완전히 격리됨)에서 정확한 값으로
        // 검증한다. 여기서는 응답 형태(음수가 아님)만 확인한다.
        assertThat(putResponse.getBody().excludedFutureSlotCount()).isGreaterThanOrEqualTo(0);
        assertThat(putResponse.getBody().keptConfirmedOrderCount()).isGreaterThanOrEqualTo(0);
        assertThat(putResponse.getBody().appliedFrom()).isNotNull();

        ResponseEntity<StoreSettingsResponse> getResponse = restTemplate.exchange(
                "/api/admin/store-settings", HttpMethod.GET,
                new HttpEntity<>(bearer(adminToken)), StoreSettingsResponse.class);

        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().openTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(getResponse.getBody().closeTime()).isEqualTo(LocalTime.of(21, 0));
        assertThat(getResponse.getBody().defaultSlotCapacity()).isEqualTo(12);
        assertThat(getResponse.getBody().holidays()).containsExactly(LocalDate.of(2026, 9, 1));
    }

    @Test
    @DisplayName("휴무일_목록은_전체_교체된다")
    void 휴무일_목록은_전체_교체된다() {
        String adminToken = issueAdminToken();
        UpdateStoreSettingsRequest firstRequest = new UpdateStoreSettingsRequest(
                DEFAULT_OPEN, DEFAULT_CLOSE, (int) DEFAULT_CAPACITY,
                List.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5)));
        restTemplate.exchange(
                "/api/admin/store-settings", HttpMethod.PUT,
                new HttpEntity<>(firstRequest, bearer(adminToken)), UpdateStoreSettingsResponse.class);

        UpdateStoreSettingsRequest secondRequest = new UpdateStoreSettingsRequest(
                DEFAULT_OPEN, DEFAULT_CLOSE, (int) DEFAULT_CAPACITY, List.of(LocalDate.of(2026, 10, 3)));
        ResponseEntity<UpdateStoreSettingsResponse> secondResponse = restTemplate.exchange(
                "/api/admin/store-settings", HttpMethod.PUT,
                new HttpEntity<>(secondRequest, bearer(adminToken)), UpdateStoreSettingsResponse.class);

        assertThat(secondResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody().holidays()).containsExactly(LocalDate.of(2026, 10, 3));

        ResponseEntity<StoreSettingsResponse> getResponse = restTemplate.exchange(
                "/api/admin/store-settings", HttpMethod.GET,
                new HttpEntity<>(bearer(adminToken)), StoreSettingsResponse.class);

        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().holidays()).containsExactly(LocalDate.of(2026, 10, 3));
    }
}
