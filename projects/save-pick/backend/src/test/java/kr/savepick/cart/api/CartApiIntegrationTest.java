package kr.savepick.cart.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.common.response.ErrorResponse;
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
 * API-012~016 HTTP 경로 (11-api-spec.md §3, 14-project-structure.md §10 api/).
 * SecurityConfig의 permitAll 경로 규칙까지 포함해 실제 HTTP로 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class CartApiIntegrationTest {

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

    @Test
    @DisplayName("게스트_토큰도_인증_토큰도_없이_장바구니를_조회하면_400_VALIDATION_ERROR다")
    void 게스트_토큰도_인증_토큰도_없이_장바구니를_조회하면_400_VALIDATION_ERROR다() {
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity("/api/cart", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("게스트_토큰_헤더가_없으면_담기_응답에_새_토큰이_발급된다")
    void 게스트_토큰_헤더가_없으면_담기_응답에_새_토큰이_발급된다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("자동발급상품", 10, adminId);
        AddCartItemRequest request = new AddCartItemRequest(product.getId(), 1);

        ResponseEntity<AddCartItemResponse> response =
                restTemplate.postForEntity("/api/cart/items", request, AddCartItemResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().guestToken()).isNotNull();
    }

    @Test
    @DisplayName("기존_게스트_토큰으로_담으면_응답에_새_토큰을_다시_발급하지_않는다")
    void 기존_게스트_토큰으로_담으면_응답에_새_토큰을_다시_발급하지_않는다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("기존토큰상품", 10, adminId);
        UUID guestToken = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Guest-Token", guestToken.toString());
        HttpEntity<AddCartItemRequest> entity = new HttpEntity<>(new AddCartItemRequest(product.getId(), 1), headers);

        ResponseEntity<AddCartItemResponse> response =
                restTemplate.exchange("/api/cart/items", HttpMethod.POST, entity, AddCartItemResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().guestToken()).isNull();
    }

    @Test
    @DisplayName("게스트_토큰으로_담고_같은_토큰으로_조회하면_담은_품목이_보인다")
    void 게스트_토큰으로_담고_같은_토큰으로_조회하면_담은_품목이_보인다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("조회상품", 10, adminId);
        UUID guestToken = UUID.randomUUID();
        HttpHeaders postHeaders = new HttpHeaders();
        postHeaders.set("X-Guest-Token", guestToken.toString());
        restTemplate.exchange("/api/cart/items", HttpMethod.POST,
                new HttpEntity<>(new AddCartItemRequest(product.getId(), 2), postHeaders), AddCartItemResponse.class);

        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.set("X-Guest-Token", guestToken.toString());
        ResponseEntity<CartResponse> response =
                restTemplate.exchange("/api/cart", HttpMethod.GET, new HttpEntity<>(getHeaders), CartResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().items()).hasSize(1);
        assertThat(response.getBody().items().get(0).quantity()).isEqualTo(2);
        assertThat(response.getBody().guestToken()).isEqualTo(guestToken);
    }

    @Test
    @DisplayName("남의_장바구니_품목을_삭제하면_404_NOT_FOUND다")
    void 남의_장바구니_품목을_삭제하면_404_NOT_FOUND다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("삭제타인상품", 10, adminId);
        UUID owner = UUID.randomUUID();
        HttpHeaders ownerHeaders = new HttpHeaders();
        ownerHeaders.set("X-Guest-Token", owner.toString());
        ResponseEntity<AddCartItemResponse> added = restTemplate.exchange("/api/cart/items", HttpMethod.POST,
                new HttpEntity<>(new AddCartItemRequest(product.getId(), 1), ownerHeaders), AddCartItemResponse.class);
        Long cartItemId = added.getBody().cartItemId();

        HttpHeaders strangerHeaders = new HttpHeaders();
        strangerHeaders.set("X-Guest-Token", UUID.randomUUID().toString());
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/cart/items/" + cartItemId, HttpMethod.DELETE, new HttpEntity<>(strangerHeaders), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("품목_삭제는_204를_반환한다")
    void 품목_삭제는_204를_반환한다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("정상삭제상품", 10, adminId);
        UUID guestToken = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Guest-Token", guestToken.toString());
        ResponseEntity<AddCartItemResponse> added = restTemplate.exchange("/api/cart/items", HttpMethod.POST,
                new HttpEntity<>(new AddCartItemRequest(product.getId(), 1), headers), AddCartItemResponse.class);
        Long cartItemId = added.getBody().cartItemId();

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/cart/items/" + cartItemId, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
