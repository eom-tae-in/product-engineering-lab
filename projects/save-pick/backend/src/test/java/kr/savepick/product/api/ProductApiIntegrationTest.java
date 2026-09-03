package kr.savepick.product.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.common.response.ErrorResponse;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.application.ProductStatusService;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductRepository;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.store.domain.Store;
import kr.savepick.stock.application.StockAdjustService;
import kr.savepick.support.ProductTestFixtures;
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
 * API-010·011 (11-api-spec.md §2, docs/16-test-plan.md TC-011~020). 비로그인 접근 허용을
 * SecurityConfig 경로 규칙까지 포함해 실제 HTTP로 검증한다 (14-project-structure.md §10 api/).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class ProductApiIntegrationTest {

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
    private ProductRepository productRepository;

    @Autowired
    private ServerClock serverClock;

    private Long registerAdmin() {
        Member admin = Member.registerAdmin(
                "admin-" + java.util.UUID.randomUUID() + "@test.com", "hash", "관리자", "01000000000", serverClock.now());
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

    /**
     * 마감 시각이 이미 지난 ON_SALE 상품을 만든다. 등록 서비스는 미래 마감만 허용하므로
     * (BR-003) 도메인 팩토리로 직접 저장한다 — BATCH-02가 아직 돌지 않아 status가 ON_SALE로
     * 남아 있는 상황을 재현한다.
     */
    private Product saveOverdueOnSaleProduct(String name) {
        LocalDateTime now = serverClock.now();
        Product product = Product.register(
                Store.SINGLETON_ID, name, "설명", "1개", 12000, now.minusMinutes(1), (short) 5, now.minusHours(2));
        product.startSale(now.minusHours(2));
        return productRepository.save(product);
    }

    @Test
    @DisplayName("TC_070_마감_시각이_지난_상품은_배치를_기다리지_않고_목록에서_즉시_빠진다")
    void TC_070_마감_시각이_지난_상품은_배치를_기다리지_않고_목록에서_즉시_빠진다() {
        Long adminId = registerAdmin();
        Product open = onSaleProduct("마감전 상품 " + java.util.UUID.randomUUID(), 10, adminId);
        Product overdue = saveOverdueOnSaleProduct("마감된 상품 " + java.util.UUID.randomUUID());

        ResponseEntity<ProductListResponse> response =
                restTemplate.getForEntity("/api/products?size=100", ProductListResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Long> ids = response.getBody().items().stream().map(ProductListItemResponse::productId).toList();
        // 11번 §11 BATCH-02 보완 — 조회 API가 배치를 기다리지 않고 즉시 제외한다(FR-014·FR-034).
        assertThat(ids).contains(open.getId()).doesNotContain(overdue.getId());
    }

    @Test
    @DisplayName("TC_009_비로그인으로_상품_목록을_조회할_수_있다")
    void TC_009_비로그인으로_상품_목록을_조회할_수_있다() {
        Long adminId = registerAdmin();
        onSaleProduct("공개목록 상품", 10, adminId);

        ResponseEntity<ProductListResponse> response = restTemplate.getForEntity("/api/products", ProductListResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().items()).isNotEmpty();
        assertThat(response.getBody().serverTime()).isNotNull();
    }

    @Test
    @DisplayName("size가_100을_넘으면_VALIDATION_ERROR다")
    void size가_100을_넘으면_VALIDATION_ERROR다() {
        ResponseEntity<ErrorResponse> response =
                restTemplate.getForEntity("/api/products?size=101", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("허용되지_않는_sort_값이면_VALIDATION_ERROR다")
    void 허용되지_않는_sort_값이면_VALIDATION_ERROR다() {
        ResponseEntity<ErrorResponse> response =
                restTemplate.getForEntity("/api/products?sort=INVALID", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("비로그인으로_상품_상세를_조회할_수_있다")
    void 비로그인으로_상품_상세를_조회할_수_있다() {
        Long adminId = registerAdmin();
        Product product = onSaleProduct("공개상세 상품", 5, adminId);

        ResponseEntity<ProductDetailResponse> response =
                restTemplate.getForEntity("/api/products/" + product.getId(), ProductDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().productId()).isEqualTo(product.getId());
        assertThat(response.getBody().purchasable()).isTrue();
    }

    @Test
    @DisplayName("존재하지_않는_상품_상세는_404다")
    void 존재하지_않는_상품_상세는_404다() {
        ResponseEntity<ErrorResponse> response =
                restTemplate.getForEntity("/api/products/999999999", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }
}
