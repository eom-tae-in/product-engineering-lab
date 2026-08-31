package kr.savepick.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.Map;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.common.audit.ActorType;
import kr.savepick.common.error.BusinessException;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.stock.application.InventoryHoldService;
import kr.savepick.stock.application.StockAdjustService;
import kr.savepick.support.ProductTestFixtures;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-010·011 (11-api-spec.md §2, BR-004, BR-006, BR-030, docs/16-test-plan.md TC-011~020).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class ProductQueryServiceIntegrationTest {

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private ProductStatusService productStatusService;

    @Autowired
    private ProductQueryService productQueryService;

    @Autowired
    private StockAdjustService stockAdjustService;

    @Autowired
    private InventoryHoldService inventoryHoldService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ServerClock serverClock;

    private Long adminId;
    private Long customerId;

    private Long registerAdmin() {
        Member admin = Member.registerAdmin(
                "admin-" + java.util.UUID.randomUUID() + "@test.com", "hash", "관리자", "01000000000", serverClock.now());
        return memberRepository.save(admin).getId();
    }

    private Long registerCustomer() {
        Member customer = Member.registerCustomer(
                "customer-" + java.util.UUID.randomUUID() + "@test.com", "hash", "고객", "01011112222", serverClock.now());
        return memberRepository.save(customer).getId();
    }

    private Long insertDummyOrder(Long memberId) {
        LocalDateTime now = serverClock.now();
        String orderNo = "ORD-" + String.format("%015d", System.nanoTime() % 1_000_000_000_000_000L);
        return jdbcTemplate.queryForObject(
                "INSERT INTO orders (order_no, member_id, status, total_amount, contact_name, contact_phone, hold_expires_at) "
                        + "VALUES (?, ?, 'PENDING', 0, '테스트', '01000000000', ?) RETURNING id",
                Long.class, orderNo, memberId, java.sql.Timestamp.valueOf(now.plusMinutes(10)));
    }

    private Product onSaleProduct(String name, int totalQuantity) {
        LocalDateTime now = serverClock.now();
        Product product = productRegisterService.register(name, "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, adminId);
        stockAdjustService.adjust(product.getId(), totalQuantity, null, adminId);
        productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);
        return product;
    }

    private void setUpActors() {
        if (adminId == null) {
            adminId = registerAdmin();
            customerId = registerCustomer();
        }
    }

    @Test
    @DisplayName("TC_011_판매_중_상품만_목록에_노출된다")
    void TC_011_판매_중_상품만_목록에_노출된다() {
        setUpActors();
        Product onSale = onSaleProduct("판매중 상품", 10);
        LocalDateTime now = serverClock.now();
        productRegisterService.register("초안 상품", "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, adminId);

        var result = productQueryService.getPublicList(null, ProductSort.CLOSING_SOON, false, 0, 20);

        assertThat(result.items()).extracting(ProductQueryService.PublicListItem::productId).contains(onSale.getId());
        assertThat(result.items()).extracting(ProductQueryService.PublicListItem::name).doesNotContain("초안 상품");
    }

    @Test
    @DisplayName("TC_012_잔여_수량_0이면_soldOut이_true다")
    void TC_012_잔여_수량_0이면_soldOut이_true다() {
        setUpActors();
        Product product = onSaleProduct("품절 상품", 1);
        Long orderId = insertDummyOrder(customerId);
        inventoryHoldService.createHolds(orderId, Map.of(product.getId(), 1), ActorType.CUSTOMER, customerId);

        var result = productQueryService.getPublicList(null, ProductSort.CLOSING_SOON, false, 0, 20);

        var item = result.items().stream().filter(i -> i.productId().equals(product.getId())).findFirst().orElseThrow();
        assertThat(item.soldOut()).isTrue();
        assertThat(item.availableQuantity()).isZero();
    }

    @Test
    @DisplayName("TC_013_검색어_앞뒤_공백과_대소문자를_무시한다")
    void TC_013_검색어_앞뒤_공백과_대소문자를_무시한다() {
        setUpActors();
        onSaleProduct("Samgyeopsal Special", 5);

        var result = productQueryService.getPublicList("  samgyeopsal  ", ProductSort.CLOSING_SOON, false, 0, 20);

        assertThat(result.items()).isNotEmpty();
    }

    @Test
    @DisplayName("TC_014_숨김_상품은_검색_결과에_나오지_않는다")
    void TC_014_숨김_상품은_검색_결과에_나오지_않는다() {
        setUpActors();
        Product hidden = onSaleProduct("숨김대상 상품", 5);
        productStatusService.changeStatus(hidden.getId(), ProductStatus.HIDDEN, adminId);

        var result = productQueryService.getPublicList("숨김대상", ProductSort.CLOSING_SOON, false, 0, 20);

        assertThat(result.items()).isEmpty();
    }

    @Test
    @DisplayName("TC_020_잔여_수량이_5_이하면_lowStock이_true다")
    void TC_020_잔여_수량이_5_이하면_lowStock이_true다() {
        setUpActors();
        Product product = onSaleProduct("소진임박 상품", 10);
        Long orderId = insertDummyOrder(customerId);
        inventoryHoldService.createHolds(orderId, Map.of(product.getId(), 3), ActorType.CUSTOMER, customerId);
        Long orderId2 = insertDummyOrder(registerCustomer());
        inventoryHoldService.createHolds(orderId2, Map.of(product.getId(), 2), ActorType.CUSTOMER, customerId);

        var result = productQueryService.getPublicList(null, ProductSort.CLOSING_SOON, false, 0, 20);

        var item = result.items().stream().filter(i -> i.productId().equals(product.getId())).findFirst().orElseThrow();
        assertThat(item.availableQuantity()).isEqualTo(5);
        assertThat(item.lowStock()).isTrue();
    }

    @Test
    @DisplayName("존재하지_않는_상품_상세는_NOT_FOUND다")
    void 존재하지_않는_상품_상세는_NOT_FOUND다() {
        assertThatThrownBy(() -> productQueryService.getPublicDetail(999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("DRAFT_상품_상세는_NOT_FOUND다")
    void DRAFT_상품_상세는_NOT_FOUND다() {
        setUpActors();
        LocalDateTime now = serverClock.now();
        Product draft = productRegisterService.register("초안", "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, adminId);

        assertThatThrownBy(() -> productQueryService.getPublicDetail(draft.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("HIDDEN_상품_상세는_PRODUCT_NOT_ON_SALE이다")
    void HIDDEN_상품_상세는_PRODUCT_NOT_ON_SALE이다() {
        setUpActors();
        Product product = onSaleProduct("숨김 상품", 5);
        productStatusService.changeStatus(product.getId(), ProductStatus.HIDDEN, adminId);

        assertThatThrownBy(() -> productQueryService.getPublicDetail(product.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_ON_SALE);
    }

    @Test
    @DisplayName("TC_016_상품_상세에서_구매가능_여부와_잔여수량을_확인한다")
    void TC_016_상품_상세에서_구매가능_여부와_잔여수량을_확인한다() {
        setUpActors();
        Product product = onSaleProduct("상세 상품", 5);

        var detail = productQueryService.getPublicDetail(product.getId());

        assertThat(detail.purchasable()).isTrue();
        assertThat(detail.quantities().available()).isEqualTo(5);
    }
}
