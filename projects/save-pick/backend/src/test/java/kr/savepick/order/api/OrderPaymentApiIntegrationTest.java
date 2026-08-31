package kr.savepick.order.api;

import static org.assertj.core.api.Assertions.assertThat;

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
import kr.savepick.order.payment.DefaultVirtualPaymentGateway;
import kr.savepick.pickup.application.PickupSlotProvisionService;
import kr.savepick.pickup.application.PickupSlotQueryService;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.application.ProductStatusService;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.stock.application.StockAdjustService;
import kr.savepick.support.ProductTestFixtures;
import kr.savepick.support.TestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
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
 * API-022~025 (11-api-spec.md §5, docs/16-test-plan.md TC-045·051·055~059·066·111·112).
 * HTTP 요청 단위로 검증한다(OrderApiIntegrationTest와 같은 패턴).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class OrderPaymentApiIntegrationTest {

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

    @Autowired
    private DefaultVirtualPaymentGateway virtualPaymentGateway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void resetGateway() {
        virtualPaymentGateway.reset();
    }

    private Long registerCustomerId() {
        Member customer = Member.registerCustomer(
                "customer-" + UUID.randomUUID() + "@test.com", passwordHasher.hash("password123"), "고객", "01011112222", serverClock.now());
        return memberRepository.save(customer).getId();
    }

    private Long registerAdminId() {
        Member admin = Member.registerAdmin(
                "admin-" + UUID.randomUUID() + "@test.com", passwordHasher.hash("adminpass1"), "관리자", "01099998888", serverClock.now());
        return memberRepository.save(admin).getId();
    }

    private String issueToken(Long memberId, Role role) {
        return tokenIssuer.issue(memberId, role, UUID.randomUUID(), serverClock.now()).token();
    }

    private Product registerOnSaleProduct(int stock, Long actorId) {
        LocalDateTime now = serverClock.now();
        Product product = productRegisterService.register(
                "결제API테스트상품-" + UUID.randomUUID(), "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, actorId);
        stockAdjustService.adjust(product.getId(), stock, null, actorId);
        productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, actorId);
        return product;
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders bearerWithIdempotencyKey(String token, String key) {
        HttpHeaders headers = bearer(token);
        headers.add("Idempotency-Key", key);
        return headers;
    }

    /** 주문서 생성 → 슬롯 지정까지 끝낸 orderId·totalAmount를 돌려준다. */
    private record PreparedOrder(Long orderId, int totalAmount) {
    }

    private PreparedOrder prepareOrder(Long customerId, String token, int stock, int quantity) {
        Product product = registerOnSaleProduct(stock, customerId);
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), quantity);
        LocalDate today = serverClock.now().toLocalDate();
        pickupSlotProvisionService.provisionForDate(today, serverClock.now());
        pickupSlotProvisionService.provisionForDate(today.plusDays(1), serverClock.now());

        ResponseEntity<OrderDraftResponse> created = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(new CreateOrderRequest(null), bearer(token)), OrderDraftResponse.class);
        Long orderId = created.getBody().orderId();

        ResponseEntity<SelectableSlotsResponse> slotsResponse = restTemplate.exchange(
                "/api/orders/" + orderId + "/pickup-slots", HttpMethod.GET, new HttpEntity<>(bearer(token)), SelectableSlotsResponse.class);
        var selectable = slotsResponse.getBody().slots().stream()
                .filter(SelectableSlotResponse::selectable)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("선택 가능한 시간대가 없다 — 테스트 전제가 깨졌다"));
        restTemplate.exchange(
                "/api/orders/" + orderId + "/pickup-slot", HttpMethod.PATCH,
                new HttpEntity<>(new AssignPickupSlotRequest(selectable.slotId()), bearer(token)), AssignPickupSlotResponse.class);

        return new PreparedOrder(orderId, created.getBody().totalAmount());
    }

    @Test
    @DisplayName("TC_112_관리자_토큰으로_고객_주문서_생성을_시도하면_403이다")
    void TC_112_관리자_토큰으로_고객_주문서_생성을_시도하면_403이다() {
        String adminToken = issueToken(registerAdminId(), Role.ADMIN);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(new CreateOrderRequest(null), bearer(adminToken)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("결제_요청에_Idempotency_Key_헤더가_없으면_VALIDATION_ERROR다")
    void 결제_요청에_Idempotency_Key_헤더가_없으면_VALIDATION_ERROR다() {
        Long customerId = registerCustomerId();
        String token = issueToken(customerId, Role.CUSTOMER);
        PreparedOrder prepared = prepareOrder(customerId, token, 5, 1);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/orders/" + prepared.orderId() + "/payments", HttpMethod.POST,
                new HttpEntity<>(new PaymentRequest(prepared.totalAmount()), bearer(token)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("TC_045_가상_결제가_성공하면_200과_함께_CONFIRMED_상태를_반환한다")
    void TC_045_가상_결제가_성공하면_200과_함께_CONFIRMED_상태를_반환한다() {
        Long customerId = registerCustomerId();
        String token = issueToken(customerId, Role.CUSTOMER);
        PreparedOrder prepared = prepareOrder(customerId, token, 5, 1);

        ResponseEntity<PaymentResponse> response = restTemplate.exchange(
                "/api/orders/" + prepared.orderId() + "/payments", HttpMethod.POST,
                new HttpEntity<>(new PaymentRequest(prepared.totalAmount()), bearerWithIdempotencyKey(token, UUID.randomUUID().toString())),
                PaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().result()).isEqualTo("SUCCEEDED");
        assertThat(response.getBody().status()).isEqualTo("CONFIRMED");
        assertThat(response.getBody().pickupNumber()).isNotBlank();
    }

    @Test
    @DisplayName("TC_051_같은_Idempotency_Key로_두번_보내면_같은_결과가_반환된다")
    void TC_051_같은_Idempotency_Key로_두번_보내면_같은_결과가_반환된다() {
        Long customerId = registerCustomerId();
        String token = issueToken(customerId, Role.CUSTOMER);
        PreparedOrder prepared = prepareOrder(customerId, token, 5, 1);
        String key = UUID.randomUUID().toString();

        ResponseEntity<PaymentResponse> first = restTemplate.exchange(
                "/api/orders/" + prepared.orderId() + "/payments", HttpMethod.POST,
                new HttpEntity<>(new PaymentRequest(prepared.totalAmount()), bearerWithIdempotencyKey(token, key)), PaymentResponse.class);
        ResponseEntity<PaymentResponse> second = restTemplate.exchange(
                "/api/orders/" + prepared.orderId() + "/payments", HttpMethod.POST,
                new HttpEntity<>(new PaymentRequest(prepared.totalAmount()), bearerWithIdempotencyKey(token, key)), PaymentResponse.class);

        assertThat(first.getBody().pickupNumber()).isEqualTo(second.getBody().pickupNumber());
    }

    @Test
    @DisplayName("주문_목록과_상세를_조회할_수_있고_취소_가능_여부가_함께_내려온다")
    void 주문_목록과_상세를_조회할_수_있고_취소_가능_여부가_함께_내려온다() {
        Long customerId = registerCustomerId();
        String token = issueToken(customerId, Role.CUSTOMER);
        PreparedOrder prepared = prepareOrder(customerId, token, 5, 1);
        restTemplate.exchange(
                "/api/orders/" + prepared.orderId() + "/payments", HttpMethod.POST,
                new HttpEntity<>(new PaymentRequest(prepared.totalAmount()), bearerWithIdempotencyKey(token, UUID.randomUUID().toString())),
                PaymentResponse.class);

        ResponseEntity<OrderListResponse> listResponse = restTemplate.exchange(
                "/api/orders", HttpMethod.GET, new HttpEntity<>(bearer(token)), OrderListResponse.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody().items()).anyMatch(item -> item.orderId().equals(prepared.orderId()));

        ResponseEntity<OrderDetailResponse> detailResponse = restTemplate.exchange(
                "/api/orders/" + prepared.orderId(), HttpMethod.GET, new HttpEntity<>(bearer(token)), OrderDetailResponse.class);
        assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detailResponse.getBody().status()).isEqualTo("CONFIRMED");
        assertThat(detailResponse.getBody().store()).isNotNull();
    }

    @Test
    @DisplayName("TC_058_타인_주문_상세에_접근하면_404다")
    void TC_058_타인_주문_상세에_접근하면_404다() {
        Long ownerId = registerCustomerId();
        String ownerToken = issueToken(ownerId, Role.CUSTOMER);
        PreparedOrder prepared = prepareOrder(ownerId, ownerToken, 5, 1);

        Long strangerId = registerCustomerId();
        String strangerToken = issueToken(strangerId, Role.CUSTOMER);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/orders/" + prepared.orderId(), HttpMethod.GET, new HttpEntity<>(bearer(strangerToken)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("TC_066_confirmed_가_true가_아니면_취소가_실행되지_않는다")
    void TC_066_confirmed_가_true가_아니면_취소가_실행되지_않는다() {
        Long customerId = registerCustomerId();
        String token = issueToken(customerId, Role.CUSTOMER);
        PreparedOrder prepared = prepareOrder(customerId, token, 5, 1);
        restTemplate.exchange(
                "/api/orders/" + prepared.orderId() + "/payments", HttpMethod.POST,
                new HttpEntity<>(new PaymentRequest(prepared.totalAmount()), bearerWithIdempotencyKey(token, UUID.randomUUID().toString())),
                PaymentResponse.class);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/orders/" + prepared.orderId() + "/cancel", HttpMethod.POST,
                new HttpEntity<>(new CancelOrderRequest(false), bearer(token)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("TC_059_confirmed_true로_취소하면_200과_CANCELED가_반환된다")
    void TC_059_confirmed_true로_취소하면_200과_CANCELED가_반환된다() {
        Long customerId = registerCustomerId();
        String token = issueToken(customerId, Role.CUSTOMER);
        PreparedOrder prepared = prepareOrder(customerId, token, 5, 1);
        restTemplate.exchange(
                "/api/orders/" + prepared.orderId() + "/payments", HttpMethod.POST,
                new HttpEntity<>(new PaymentRequest(prepared.totalAmount()), bearerWithIdempotencyKey(token, UUID.randomUUID().toString())),
                PaymentResponse.class);
        // prepareOrder는 "선택 가능한 첫 시간대"를 고른다 — 예약 마감(BR-015, 30분 전)보다 취소
        // 마감(BR-018, 1시간 전)이 더 일찍 닫혀서, 그 시간대가 이미 고객 취소 마감을 지났을 수
        // 있다. 이 테스트는 "정상적으로 취소 가능한 경우"만 검증하므로 마감을 여유 있게 미룬다
        // (취소 마감 경과 자체는 OrderCancelServiceIntegrationTest가 별도로 검증한다).
        jdbcTemplate.update(
                "UPDATE orders SET cancelable_until = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(serverClock.now().plusHours(1)), prepared.orderId());

        ResponseEntity<OrderCancelResponse> response = restTemplate.exchange(
                "/api/orders/" + prepared.orderId() + "/cancel", HttpMethod.POST,
                new HttpEntity<>(new CancelOrderRequest(true), bearer(token)), OrderCancelResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("CANCELED");
        assertThat(response.getBody().slotReleased()).isTrue();
    }
}
