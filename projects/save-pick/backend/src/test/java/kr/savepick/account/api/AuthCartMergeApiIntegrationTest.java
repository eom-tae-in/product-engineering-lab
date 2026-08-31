package kr.savepick.account.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.cart.api.AddCartItemRequest;
import kr.savepick.cart.api.AddCartItemResponse;
import kr.savepick.cart.api.CartResponse;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.application.ProductStatusService;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.stock.application.StockAdjustService;
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
 * 회원가입·로그인 시 게스트 장바구니 병합 (10-erd.md §5.1, docs/16-test-plan.md TC-021~025 범위와
 * 맞닿는 cart 슬라이스 병행 작업 — account 컨트롤러가 무시하던 guestToken을 실제로 처리한다).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class AuthCartMergeApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private ProductStatusService productStatusService;

    @Autowired
    private StockAdjustService stockAdjustService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ServerClock serverClock;

    private Long registerAdmin() {
        Member admin = Member.registerAdmin(
                "admin-" + UUID.randomUUID() + "@test.com", "hash", "관리자", "01000000000", serverClock.now());
        return memberRepository.save(admin).getId();
    }

    private Product onSaleProduct(String name, int totalQuantity, Long adminId) {
        LocalDateTime now = serverClock.now();
        Product product = productRegisterService.register(
                name, "설명", "1개", 12000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, adminId);
        stockAdjustService.adjust(product.getId(), totalQuantity, null, adminId);
        productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);
        return product;
    }

    private void addToGuestCart(UUID guestToken, Long productId, int quantity) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Guest-Token", guestToken.toString());
        ResponseEntity<AddCartItemResponse> response = restTemplate.exchange("/api/cart/items", HttpMethod.POST,
                new HttpEntity<>(new AddCartItemRequest(productId, quantity), headers), AddCartItemResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("가입_요청에_게스트_토큰이_있으면_그_장바구니가_회원_장바구니로_병합된다")
    void 가입_요청에_게스트_토큰이_있으면_그_장바구니가_회원_장바구니로_병합된다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("가입병합상품", 10, adminId);
        UUID guestToken = UUID.randomUUID();
        addToGuestCart(guestToken, product.getId(), 2);

        SignUpRequest signUpRequest = new SignUpRequest(
                "merge-signup@test.com", "password123", "가입회원", "01011119999", guestToken.toString());
        ResponseEntity<SignUpResponse> signUpResponse =
                restTemplate.postForEntity("/api/auth/signup", signUpRequest, SignUpResponse.class);

        assertThat(signUpResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(signUpResponse.getBody()).isNotNull();
        assertThat(signUpResponse.getBody().cartMerged()).isTrue();

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(signUpResponse.getBody().accessToken());
        ResponseEntity<CartResponse> cartResponse =
                restTemplate.exchange("/api/cart", HttpMethod.GET, new HttpEntity<>(authHeaders), CartResponse.class);

        assertThat(cartResponse.getBody()).isNotNull();
        assertThat(cartResponse.getBody().items()).hasSize(1);
        assertThat(cartResponse.getBody().items().get(0).quantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("로그인_요청에_게스트_토큰이_있으면_기존_회원_장바구니와_수량이_합산된다")
    void 로그인_요청에_게스트_토큰이_있으면_기존_회원_장바구니와_수량이_합산된다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("로그인병합상품", 10, adminId);

        SignUpRequest signUpRequest = new SignUpRequest(
                "merge-login@test.com", "password123", "로그인회원", "01022223333", null);
        ResponseEntity<SignUpResponse> signUpResponse =
                restTemplate.postForEntity("/api/auth/signup", signUpRequest, SignUpResponse.class);
        assertThat(signUpResponse.getBody().cartMerged()).isFalse();

        HttpHeaders firstAuthHeaders = new HttpHeaders();
        firstAuthHeaders.setBearerAuth(signUpResponse.getBody().accessToken());
        restTemplate.exchange("/api/cart/items", HttpMethod.POST,
                new HttpEntity<>(new AddCartItemRequest(product.getId(), 1), firstAuthHeaders), AddCartItemResponse.class);

        UUID guestToken = UUID.randomUUID();
        addToGuestCart(guestToken, product.getId(), 2);

        LoginRequest loginRequest = new LoginRequest("merge-login@test.com", "password123", guestToken.toString());
        ResponseEntity<LoginResponse> loginResponse = restTemplate.postForEntity("/api/auth/login", loginRequest, LoginResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(loginResponse.getBody().accessToken());
        ResponseEntity<CartResponse> cartResponse =
                restTemplate.exchange("/api/cart", HttpMethod.GET, new HttpEntity<>(authHeaders), CartResponse.class);

        assertThat(cartResponse.getBody()).isNotNull();
        assertThat(cartResponse.getBody().items()).hasSize(1);
        assertThat(cartResponse.getBody().items().get(0).quantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("게스트_토큰_없이_가입하면_기존과_동일하게_cartMerged가_false다")
    void 게스트_토큰_없이_가입하면_기존과_동일하게_cartMerged가_false다() {
        SignUpRequest signUpRequest = new SignUpRequest(
                "no-guest-signup@test.com", "password123", "무게스트회원", "01033334444", null);

        ResponseEntity<SignUpResponse> response = restTemplate.postForEntity("/api/auth/signup", signUpRequest, SignUpResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().cartMerged()).isFalse();
    }

    @Test
    @DisplayName("형식이_아닌_게스트_토큰이_와도_가입_자체는_성공한다")
    void 형식이_아닌_게스트_토큰이_와도_가입_자체는_성공한다() {
        SignUpRequest signUpRequest = new SignUpRequest(
                "bad-guest-signup@test.com", "password123", "잘못된토큰회원", "01044445555", "not-a-uuid");

        ResponseEntity<SignUpResponse> response = restTemplate.postForEntity("/api/auth/signup", signUpRequest, SignUpResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().cartMerged()).isFalse();
    }
}
