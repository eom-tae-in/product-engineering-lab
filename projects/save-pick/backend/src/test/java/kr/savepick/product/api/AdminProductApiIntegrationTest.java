package kr.savepick.product.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.account.domain.Role;
import kr.savepick.account.infrastructure.BcryptPasswordHasher;
import kr.savepick.account.infrastructure.JwtAccessTokenIssuer;
import kr.savepick.common.response.ErrorResponse;
import kr.savepick.common.time.ServerClock;
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
 * API-102~108 (11-api-spec.md §7, 12-auth.md §3.1, docs/16-test-plan.md TC-071~078).
 * 권한 판정과 핵심 오류 코드를 실제 HTTP 경로로 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class AdminProductApiIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

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

    private String issueAdminToken() {
        Member admin = Member.registerAdmin(
                "admin-" + UUID.randomUUID() + "@test.com", passwordHasher.hash("adminpass1"), "관리자", "01099998888", serverClock.now());
        admin = memberRepository.save(admin);
        return tokenIssuer.issue(admin.getId(), Role.ADMIN, UUID.randomUUID(), serverClock.now()).token();
    }

    private String issueCustomerToken() {
        Member customer = Member.registerCustomer(
                "customer-" + UUID.randomUUID() + "@test.com", passwordHasher.hash("password123"), "고객", "01011112222", serverClock.now());
        customer = memberRepository.save(customer);
        return tokenIssuer.issue(customer.getId(), Role.CUSTOMER, UUID.randomUUID(), serverClock.now()).token();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private OffsetDateTime futureClosingAt() {
        OffsetDateTime now = serverClock.now().atZone(ZONE).toOffsetDateTime();
        OffsetDateTime candidate = now.plusHours(3);
        if (candidate.toLocalTime().isAfter(java.time.LocalTime.of(22, 0))) {
            return now.toLocalDate().plusDays(1).atTime(20, 0).atZone(ZONE).toOffsetDateTime();
        }
        return candidate;
    }

    @Test
    @DisplayName("토큰_없이_상품을_등록하면_401이다")
    void 토큰_없이_상품을_등록하면_401이다() {
        RegisterProductRequest request =
                new RegisterProductRequest("상품", "설명", "1개", 12000, futureClosingAt(), (short) 5);

        ResponseEntity<ErrorResponse> response =
                restTemplate.postForEntity("/api/admin/products", request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("고객_토큰으로_상품을_등록하면_403이다")
    void 고객_토큰으로_상품을_등록하면_403이다() {
        String customerToken = issueCustomerToken();
        RegisterProductRequest request =
                new RegisterProductRequest("상품", "설명", "1개", 12000, futureClosingAt(), (short) 5);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/products", HttpMethod.POST, new HttpEntity<>(request, bearer(customerToken)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("TC_071_관리자_토큰으로_상품을_등록하면_DRAFT_상태로_생성된다")
    void TC_071_관리자_토큰으로_상품을_등록하면_DRAFT_상태로_생성된다() {
        String adminToken = issueAdminToken();
        RegisterProductRequest request =
                new RegisterProductRequest("국내산 삼겹살 300g", "오늘 손질한 국내산 삼겹살입니다.", "300g", 12000, futureClosingAt(), (short) 5);

        ResponseEntity<RegisterProductResponse> response = restTemplate.exchange(
                "/api/admin/products", HttpMethod.POST, new HttpEntity<>(request, bearer(adminToken)), RegisterProductResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("DRAFT");
        assertThat(response.getBody().originalPrice()).isEqualTo(12000);
    }

    @Test
    @DisplayName("TC_072c_정가가_100원_미만이면_VALIDATION_ERROR다")
    void TC_072c_정가가_100원_미만이면_VALIDATION_ERROR다() {
        String adminToken = issueAdminToken();
        RegisterProductRequest request = new RegisterProductRequest("상품", "설명", "1개", 50, futureClosingAt(), (short) 5);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/products", HttpMethod.POST, new HttpEntity<>(request, bearer(adminToken)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("등록한_상품을_관리자_상세로_조회하면_stock_필드가_포함된다")
    void 등록한_상품을_관리자_상세로_조회하면_stock_필드가_포함된다() {
        String adminToken = issueAdminToken();
        RegisterProductRequest request = new RegisterProductRequest("상세조회 상품", "설명", "1개", 12000, futureClosingAt(), (short) 5);
        ResponseEntity<RegisterProductResponse> created = restTemplate.exchange(
                "/api/admin/products", HttpMethod.POST, new HttpEntity<>(request, bearer(adminToken)), RegisterProductResponse.class);
        Long productId = created.getBody().productId();

        ResponseEntity<AdminProductDetailResponse> response = restTemplate.exchange(
                "/api/admin/products/" + productId, HttpMethod.GET, new HttpEntity<>(bearer(adminToken)), AdminProductDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().stock()).isNotNull();
        assertThat(response.getBody().stock().totalQuantity()).isZero();
    }

    @Test
    @DisplayName("TC_075a_재고_미등록_상품을_ON_SALE로_전환하면_409다")
    void TC_075a_재고_미등록_상품을_ON_SALE로_전환하면_409다() {
        String adminToken = issueAdminToken();
        RegisterProductRequest request = new RegisterProductRequest("상태전환 상품", "설명", "1개", 12000, futureClosingAt(), (short) 5);
        ResponseEntity<RegisterProductResponse> created = restTemplate.exchange(
                "/api/admin/products", HttpMethod.POST, new HttpEntity<>(request, bearer(adminToken)), RegisterProductResponse.class);
        Long productId = created.getBody().productId();

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/products/" + productId + "/status", HttpMethod.PATCH,
                new HttpEntity<>(new ChangeProductStatusRequest("ON_SALE"), bearer(adminToken)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("PRODUCT_STATUS_TRANSITION_DENIED");
    }

    @Test
    @DisplayName("TC_078_할인_구간_정책은_editable이_false다")
    void TC_078_할인_구간_정책은_editable이_false다() {
        String adminToken = issueAdminToken();

        ResponseEntity<DiscountPolicyResponse> response = restTemplate.exchange(
                "/api/admin/discount-policy", HttpMethod.GET, new HttpEntity<>(bearer(adminToken)), DiscountPolicyResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().editable()).isFalse();
        assertThat(response.getBody().tiers()).hasSize(4);
    }

    @Test
    @DisplayName("존재하지_않는_상품_수정_이력_조회는_404다")
    void 존재하지_않는_상품_수정_이력_조회는_404다() {
        String adminToken = issueAdminToken();

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/products/999999999/change-logs", HttpMethod.GET, new HttpEntity<>(bearer(adminToken)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
