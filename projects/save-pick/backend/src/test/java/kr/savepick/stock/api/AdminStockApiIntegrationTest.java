package kr.savepick.stock.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.account.domain.Role;
import kr.savepick.account.infrastructure.BcryptPasswordHasher;
import kr.savepick.account.infrastructure.JwtAccessTokenIssuer;
import kr.savepick.common.response.ErrorResponse;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.domain.Product;
import kr.savepick.support.ProductTestFixtures;
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
 * API-109~111 (11-api-spec.md §8, 12-auth.md §3.1, docs/16-test-plan.md TC-079~083).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class AdminStockApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductRegisterService productRegisterService;

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

    private Product registerProduct(String name) {
        LocalDateTime now = serverClock.now();
        return productRegisterService.register(name, "설명", "1개", 12000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, 1L);
    }

    @Test
    @DisplayName("고객_토큰으로_재고를_조정하면_403이다")
    void 고객_토큰으로_재고를_조정하면_403이다() {
        Product product = registerProduct("재고조정 권한 상품");
        String customerToken = issueCustomerToken();
        AdjustStockRequest request = new AdjustStockRequest(10, "메모");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/products/" + product.getId() + "/stock", HttpMethod.PUT,
                new HttpEntity<>(request, bearer(customerToken)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("TC_079_관리자_토큰으로_재고를_등록하면_판매가능수량이_늘어난다")
    void TC_079_관리자_토큰으로_재고를_등록하면_판매가능수량이_늘어난다() {
        Product product = registerProduct("재고등록 상품");
        String adminToken = issueAdminToken();
        AdjustStockRequest request = new AdjustStockRequest(17, "실물 진열 수량 확인");

        ResponseEntity<AdjustStockResponse> response = restTemplate.exchange(
                "/api/admin/products/" + product.getId() + "/stock", HttpMethod.PUT,
                new HttpEntity<>(request, bearer(adminToken)), AdjustStockResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().after().totalQuantity()).isEqualTo(17);
        assertThat(response.getBody().after().availableQuantity()).isEqualTo(17);
    }

    @Test
    @DisplayName("TC_081_음수_재고는_VALIDATION_ERROR다")
    void TC_081_음수_재고는_VALIDATION_ERROR다() {
        Product product = registerProduct("음수재고 상품");
        String adminToken = issueAdminToken();
        AdjustStockRequest request = new AdjustStockRequest(-1, null);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/products/" + product.getId() + "/stock", HttpMethod.PUT,
                new HttpEntity<>(request, bearer(adminToken)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("TC_082_재고_현황을_조회하면_4값이_일관된다")
    void TC_082_재고_현황을_조회하면_4값이_일관된다() {
        Product product = registerProduct("현황조회 상품");
        String adminToken = issueAdminToken();
        restTemplate.exchange(
                "/api/admin/products/" + product.getId() + "/stock", HttpMethod.PUT,
                new HttpEntity<>(new AdjustStockRequest(10, null), bearer(adminToken)), AdjustStockResponse.class);

        // 이 클래스는 다른 RANDOM_PORT 통합 테스트 클래스들과 같은 컨테이너/DB를 공유한다
        // (Spring 컨텍스트 캐싱 — 같은 설정의 @SpringBootTest는 컨테이너를 재사용한다). 전체
        // 스위트가 누적해 만든 상품 수가 기본 페이지 크기(20)를 넘을 수 있어, 이 테스트가 방금
        // 만든 상품이 한 페이지 안에 들어오도록 충분히 큰 size를 명시적으로 요청한다.
        ResponseEntity<StockOverviewListResponse> response = restTemplate.exchange(
                "/api/admin/stocks?size=500", HttpMethod.GET, new HttpEntity<>(bearer(adminToken)), StockOverviewListResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().items()).anySatisfy(item -> {
            assertThat(item.productId()).isEqualTo(product.getId());
            assertThat(item.consistent()).isTrue();
        });
    }

    @Test
    @DisplayName("TC_083_재고_변경_이력을_조회할_수_있다")
    void TC_083_재고_변경_이력을_조회할_수_있다() {
        Product product = registerProduct("이력조회 상품");
        String adminToken = issueAdminToken();
        restTemplate.exchange(
                "/api/admin/products/" + product.getId() + "/stock", HttpMethod.PUT,
                new HttpEntity<>(new AdjustStockRequest(10, "최초 등록"), bearer(adminToken)), AdjustStockResponse.class);

        ResponseEntity<StockLedgerListResponse> response = restTemplate.exchange(
                "/api/admin/stocks/" + product.getId() + "/ledger", HttpMethod.GET,
                new HttpEntity<>(bearer(adminToken)), StockLedgerListResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().items()).hasSize(1);
        assertThat(response.getBody().items().get(0).reason()).isEqualTo("ADMIN_ADJUST");
    }

    @Test
    @DisplayName("존재하지_않는_상품의_재고_이력_조회는_404다")
    void 존재하지_않는_상품의_재고_이력_조회는_404다() {
        String adminToken = issueAdminToken();

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/stocks/999999999/ledger", HttpMethod.GET, new HttpEntity<>(bearer(adminToken)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
