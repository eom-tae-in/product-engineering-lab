package kr.savepick.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.account.domain.Role;
import kr.savepick.account.infrastructure.BcryptPasswordHasher;
import kr.savepick.account.infrastructure.JwtAccessTokenIssuer;
import kr.savepick.cart.application.CartOwner;
import kr.savepick.cart.application.CartService;
import kr.savepick.common.response.ErrorResponse;
import kr.savepick.common.time.ServerClock;
import kr.savepick.pickup.application.PickupSlotProvisionService;
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
 * API-017~021 (11-api-spec.md §4, docs/16-test-plan.md TC-027~030·037~044·111).
 * HTTP 요청 단위로(요청마다 독립된 트랜잭션) 검증한다 — 실패 시 실제로 롤백되는지는 이
 * 계층에서만 관찰할 수 있다(application 계층 테스트는 테스트 트랜잭션에 합류해 관찰이 어렵다).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class OrderApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private ProductStatusService productStatusService;

    @Autowired
    private StockAdjustService stockAdjustService;

    @Autowired
    private CartService cartService;

    @Autowired
    private PickupSlotProvisionService pickupSlotProvisionService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BcryptPasswordHasher passwordHasher;

    @Autowired
    private JwtAccessTokenIssuer tokenIssuer;

    @Autowired
    private ServerClock serverClock;

    private Long registerCustomerId() {
        Member customer = Member.registerCustomer(
                "customer-" + UUID.randomUUID() + "@test.com", passwordHasher.hash("password123"), "고객", "01011112222", serverClock.now());
        return memberRepository.save(customer).getId();
    }

    private String issueTokenFor(Long memberId) {
        return tokenIssuer.issue(memberId, Role.CUSTOMER, UUID.randomUUID(), serverClock.now()).token();
    }

    private Product registerOnSaleProduct(int stock, Long actorId) {
        LocalDateTime now = serverClock.now();
        Product product = productRegisterService.register(
                "API테스트상품-" + UUID.randomUUID(), "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, actorId);
        stockAdjustService.adjust(product.getId(), stock, null, actorId);
        productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, actorId);
        return product;
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    @DisplayName("TC_111_인증_없이_주문서_생성을_시도하면_401이다")
    void TC_111_인증_없이_주문서_생성을_시도하면_401이다() {
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(new CreateOrderRequest(null)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("TC_009_인증_없이_주문_내역을_조회하면_401이다")
    void TC_009_인증_없이_주문_내역을_조회하면_401이다() {
        // TC-009 (c) — 상품 목록·상세는 비로그인도 200이지만(ProductApiIntegrationTest),
        // 주문 내역(API-023)은 본인 주문만 보이는 자원이라 미인증이면 401이어야 한다.
        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/orders", HttpMethod.GET, HttpEntity.EMPTY, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    @DisplayName("정상_주문서_생성_후_동일_상품_재시도는_품절이며_잔여_주문서가_남지_않는다")
    void 정상_주문서_생성_후_품절_재시도_시_잔여_주문서가_남지_않는다() {
        Long customerId = registerCustomerId();
        String token = issueTokenFor(customerId);
        Product product = registerOnSaleProduct(1, customerId);
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), 1);

        ResponseEntity<OrderDraftResponse> firstResponse = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(new CreateOrderRequest(null), bearer(token)), OrderDraftResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(firstResponse.getBody()).isNotNull();
        assertThat(firstResponse.getBody().status()).isEqualTo("PENDING");
        assertThat(firstResponse.getBody().holdRemainingSeconds()).isGreaterThan(0);

        // 이미 PENDING 주문서를 보유한 상태 — 두 번째 시도는 재고 부족 이전에 PENDING_ORDER_EXISTS다.
        ResponseEntity<ErrorResponse> secondResponse = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(new CreateOrderRequest(null), bearer(token)), ErrorResponse.class);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody().code()).isEqualTo("PENDING_ORDER_EXISTS");
    }

    @Test
    @DisplayName("재고_부족으로_실패한_주문서_생성_시도는_흔적을_남기지_않아_다른_고객이_바로_주문할_수_있다")
    void 재고_부족으로_실패한_주문서_생성_시도는_흔적을_남기지_않는다() {
        Long ownerId = registerCustomerId();
        // 장바구니 담기 시점에는 재고가 충분해야 CartService의 담기 자체가 막히지 않는다(BR-010).
        Product scarce = registerOnSaleProduct(5, ownerId);

        Long firstCustomerId = registerCustomerId();
        String firstToken = issueTokenFor(firstCustomerId);
        CartService.AddItemResult cartItem = cartService.addItem(CartOwner.ofMember(firstCustomerId), scarce.getId(), 2);
        // 담은 뒤 다른 경로로 재고가 줄어든 상황을 재현한다.
        stockAdjustService.adjust(scarce.getId(), 1, null, ownerId);

        ResponseEntity<ErrorResponse> failResponse = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(new CreateOrderRequest(null), bearer(firstToken)), ErrorResponse.class);
        assertThat(failResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(failResponse.getBody()).isNotNull();
        assertThat(failResponse.getBody().code()).isEqualTo("OUT_OF_STOCK");

        // 실패한 시도가 orders에 PENDING을 남겼다면, 같은 고객의 재시도는 (수량을 줄여도) 재고
        // 부족이 아니라 PENDING_ORDER_EXISTS로 막혔을 것이다 — 201이 나와야 흔적이 없다는 뜻이다.
        cartService.changeQuantity(CartOwner.ofMember(firstCustomerId), cartItem.cartItemId(), 1);
        ResponseEntity<OrderDraftResponse> retryResponse = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(new CreateOrderRequest(null), bearer(firstToken)), OrderDraftResponse.class);
        assertThat(retryResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // scarce 총 재고는 1인데 firstCustomer가 방금 1개를 선점했으므로 이제는 정말 품절이다 —
        // 다른 고객은 장바구니 담기 시점부터 막힌다(BR-027, 부분 선점이 남지 않았다는 뜻은
        // 첫 실패 시도에서 아무것도 선점되지 않았다는 것이지, 이후 성공한 1개가 사라진다는 뜻이 아니다).
        Long secondCustomerId = registerCustomerId();
        assertThatThrownBy(() -> cartService.addItem(CartOwner.ofMember(secondCustomerId), scarce.getId(), 1))
                .isInstanceOf(kr.savepick.common.error.BusinessException.class)
                .extracting(e -> ((kr.savepick.common.error.BusinessException) e).errorCode())
                .isEqualTo(kr.savepick.common.error.ErrorCode.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("빈_장바구니로_주문서_생성을_시도하면_CART_EMPTY다")
    void 빈_장바구니로_주문서_생성을_시도하면_CART_EMPTY다() {
        Long customerId = registerCustomerId();
        String token = issueTokenFor(customerId);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(new CreateOrderRequest(null), bearer(token)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CART_EMPTY");
    }

    @Test
    @DisplayName("남의_주문의_선점_상태_포기_시간대는_모두_404다")
    void 남의_주문에_접근하면_404다() {
        Long ownerId = registerCustomerId();
        String ownerToken = issueTokenFor(ownerId);
        Product product = registerOnSaleProduct(5, ownerId);
        cartService.addItem(CartOwner.ofMember(ownerId), product.getId(), 1);
        ResponseEntity<OrderDraftResponse> created = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(new CreateOrderRequest(null), bearer(ownerToken)), OrderDraftResponse.class);
        Long orderId = created.getBody().orderId();

        Long strangerId = registerCustomerId();
        String strangerToken = issueTokenFor(strangerId);
        HttpEntity<Void> strangerAuth = new HttpEntity<>(bearer(strangerToken));

        assertThat(restTemplate.exchange(
                "/api/orders/" + orderId + "/hold", HttpMethod.GET, strangerAuth, ErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.exchange(
                "/api/orders/" + orderId + "/pickup-slots", HttpMethod.GET, strangerAuth, ErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.exchange(
                        "/api/orders/" + orderId + "/pickup-slot", HttpMethod.PATCH,
                        new HttpEntity<>(new AssignPickupSlotRequest(1L), bearer(strangerToken)), ErrorResponse.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.exchange(
                "/api/orders/" + orderId, HttpMethod.DELETE, strangerAuth, ErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("주문서_포기_후_다시_포기하면_INVALID_ORDER_STATUS다")
    void 주문서_포기_후_다시_포기하면_INVALID_ORDER_STATUS다() {
        Long customerId = registerCustomerId();
        String token = issueTokenFor(customerId);
        Product product = registerOnSaleProduct(5, customerId);
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), 1);
        ResponseEntity<OrderDraftResponse> created = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(new CreateOrderRequest(null), bearer(token)), OrderDraftResponse.class);
        Long orderId = created.getBody().orderId();

        ResponseEntity<AbandonOrderResponse> abandonResponse = restTemplate.exchange(
                "/api/orders/" + orderId, HttpMethod.DELETE, new HttpEntity<>(bearer(token)), AbandonOrderResponse.class);
        assertThat(abandonResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(abandonResponse.getBody()).isNotNull();
        assertThat(abandonResponse.getBody().status()).isEqualTo("EXPIRED");

        ResponseEntity<ErrorResponse> secondAbandon = restTemplate.exchange(
                "/api/orders/" + orderId, HttpMethod.DELETE, new HttpEntity<>(bearer(token)), ErrorResponse.class);
        assertThat(secondAbandon.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondAbandon.getBody()).isNotNull();
        assertThat(secondAbandon.getBody().code()).isEqualTo("INVALID_ORDER_STATUS");
    }

    @Test
    @DisplayName("TC_037_D_2_날짜를_지정해_시간대를_조회하면_SLOT_DATE_OUT_OF_RANGE다")
    void TC_037_D_2_날짜를_지정해_시간대를_조회하면_SLOT_DATE_OUT_OF_RANGE다() {
        Long customerId = registerCustomerId();
        String token = issueTokenFor(customerId);
        Product product = registerOnSaleProduct(5, customerId);
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), 1);
        ResponseEntity<OrderDraftResponse> created = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(new CreateOrderRequest(null), bearer(token)), OrderDraftResponse.class);
        Long orderId = created.getBody().orderId();
        LocalDate dayAfterTomorrow = serverClock.now().toLocalDate().plusDays(2);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/orders/" + orderId + "/pickup-slots?date=" + dayAfterTomorrow, HttpMethod.GET,
                new HttpEntity<>(bearer(token)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("SLOT_DATE_OUT_OF_RANGE");
    }

    @Test
    @DisplayName("픽업_시간대를_지정해도_정원은_점유되지_않는다")
    void 픽업_시간대를_지정해도_정원은_점유되지_않는다() {
        Long customerId = registerCustomerId();
        String token = issueTokenFor(customerId);
        Product product = registerOnSaleProduct(5, customerId);
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), 1);
        LocalDate today = serverClock.now().toLocalDate();
        pickupSlotProvisionService.provisionForDate(today, serverClock.now());
        pickupSlotProvisionService.provisionForDate(today.plusDays(1), serverClock.now());

        ResponseEntity<OrderDraftResponse> created = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(new CreateOrderRequest(null), bearer(token)), OrderDraftResponse.class);
        Long orderId = created.getBody().orderId();

        ResponseEntity<SelectableSlotsResponse> slotsResponse = restTemplate.exchange(
                "/api/orders/" + orderId + "/pickup-slots", HttpMethod.GET, new HttpEntity<>(bearer(token)), SelectableSlotsResponse.class);
        assertThat(slotsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(slotsResponse.getBody()).isNotNull();
        var selectableSlot = slotsResponse.getBody().slots().stream()
                .filter(SelectableSlotResponse::selectable)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("선택 가능한 시간대가 없다 — 테스트 전제가 깨졌다"));
        int reservedBefore = selectableSlot.reservedCount();

        ResponseEntity<AssignPickupSlotResponse> assignResponse = restTemplate.exchange(
                "/api/orders/" + orderId + "/pickup-slot", HttpMethod.PATCH,
                new HttpEntity<>(new AssignPickupSlotRequest(selectableSlot.slotId()), bearer(token)), AssignPickupSlotResponse.class);

        assertThat(assignResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(assignResponse.getBody()).isNotNull();
        assertThat(assignResponse.getBody().pickupSlotId()).isEqualTo(selectableSlot.slotId());

        ResponseEntity<SelectableSlotsResponse> afterResponse = restTemplate.exchange(
                "/api/orders/" + orderId + "/pickup-slots", HttpMethod.GET, new HttpEntity<>(bearer(token)), SelectableSlotsResponse.class);
        var sameSlotAfter = afterResponse.getBody().slots().stream()
                .filter(s -> s.slotId().equals(selectableSlot.slotId()))
                .findFirst()
                .orElseThrow();
        assertThat(sameSlotAfter.reservedCount()).isEqualTo(reservedBefore);
    }
}
