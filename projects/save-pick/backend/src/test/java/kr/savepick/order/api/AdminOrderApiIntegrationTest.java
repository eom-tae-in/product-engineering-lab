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
import org.springframework.test.context.ActiveProfiles;

/** API-112~117 (11-api-spec.md §9, docs/16-test-plan.md TC-087~097). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class AdminOrderApiIntegrationTest {

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

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private Product registerOnSaleProduct(int stock, Long actorId) {
        LocalDateTime now = serverClock.now();
        Product product = productRegisterService.register(
                "관리자주문API상품-" + UUID.randomUUID(), "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, actorId);
        stockAdjustService.adjust(product.getId(), stock, null, actorId);
        productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, actorId);
        return product;
    }

    /** 결제까지 마쳐 CONFIRMED 상태인 주문을 만들고 orderId를 돌려준다. */
    private Long confirmOrder(Long customerId, String customerToken) {
        Product product = registerOnSaleProduct(5, customerId);
        cartService.addItem(CartOwner.ofMember(customerId), product.getId(), 1);
        LocalDate today = serverClock.now().toLocalDate();
        pickupSlotProvisionService.provisionForDate(today, serverClock.now());
        pickupSlotProvisionService.provisionForDate(today.plusDays(1), serverClock.now());

        ResponseEntity<OrderDraftResponse> created = restTemplate.exchange(
                "/api/orders", HttpMethod.POST, new HttpEntity<>(new CreateOrderRequest(null), bearer(customerToken)), OrderDraftResponse.class);
        Long orderId = created.getBody().orderId();

        ResponseEntity<SelectableSlotsResponse> slotsResponse = restTemplate.exchange(
                "/api/orders/" + orderId + "/pickup-slots", HttpMethod.GET, new HttpEntity<>(bearer(customerToken)), SelectableSlotsResponse.class);
        var selectable = slotsResponse.getBody().slots().stream()
                .filter(SelectableSlotResponse::selectable)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("선택 가능한 시간대가 없다 — 테스트 전제가 깨졌다"));
        restTemplate.exchange(
                "/api/orders/" + orderId + "/pickup-slot", HttpMethod.PATCH,
                new HttpEntity<>(new AssignPickupSlotRequest(selectable.slotId()), bearer(customerToken)), AssignPickupSlotResponse.class);

        restTemplate.exchange(
                "/api/orders/" + orderId + "/payments", HttpMethod.POST,
                new HttpEntity<>(new PaymentRequest(created.getBody().totalAmount()), withIdempotencyKey(bearer(customerToken))),
                PaymentResponse.class);

        return orderId;
    }

    private HttpHeaders withIdempotencyKey(HttpHeaders headers) {
        headers.add("Idempotency-Key", UUID.randomUUID().toString());
        return headers;
    }

    @Test
    @DisplayName("고객_토큰으로_관리자_주문_목록에_접근하면_403이다")
    void 고객_토큰으로_관리자_주문_목록에_접근하면_403이다() {
        String customerToken = issueToken(registerCustomerId(), Role.CUSTOMER);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/orders", HttpMethod.GET, new HttpEntity<>(bearer(customerToken)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("TC_089_관리자_주문_상세는_연락처와_결제이력과_상태이력을_포함한다")
    void TC_089_관리자_주문_상세는_연락처와_결제이력과_상태이력을_포함한다() {
        Long customerId = registerCustomerId();
        String customerToken = issueToken(customerId, Role.CUSTOMER);
        String adminToken = issueToken(registerAdminId(), Role.ADMIN);
        Long orderId = confirmOrder(customerId, customerToken);

        ResponseEntity<AdminOrderDetailResponse> response = restTemplate.exchange(
                "/api/admin/orders/" + orderId, HttpMethod.GET, new HttpEntity<>(bearer(adminToken)), AdminOrderDetailResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("CONFIRMED");
        assertThat(response.getBody().customer().phone()).isEqualTo("01011112222");
        assertThat(response.getBody().paymentAttempts()).hasSize(1);
        assertThat(response.getBody().paymentAttempts().get(0).status()).isEqualTo("SUCCEEDED");
        assertThat(response.getBody().statusHistory()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(response.getBody().availableActions()).containsExactlyInAnyOrder("READY", "COMPLETE", "CANCEL");

        // 06번 SC-110(주문 상세, 관리자)이 "픽업 날짜·시간대"를 표시하려면 상세 응답에도
        // 픽업 시간대가 있어야 한다. 목록(API-112)과 같은 시간대를 가리키는지 함께 확인한다.
        ResponseEntity<AdminOrderListResponse> list = restTemplate.exchange(
                "/api/admin/orders", HttpMethod.GET, new HttpEntity<>(bearer(adminToken)), AdminOrderListResponse.class);
        AdminOrderListItemResponse listItem = list.getBody().items().stream()
                .filter(item -> item.orderId().equals(orderId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("방금 확정한 주문이 목록에 없다 — 테스트 전제가 깨졌다"));
        assertThat(response.getBody().pickupStartAt()).isNotNull().isEqualTo(listItem.pickupStartAt());
        assertThat(response.getBody().pickupEndAt()).isNotNull().isEqualTo(listItem.pickupEndAt());
        assertThat(response.getBody().pickupStartAt()).isBefore(response.getBody().pickupEndAt());
    }

    @Test
    @DisplayName("TC_088_픽업_번호로_주문을_조회할_수_있고_없는_번호는_404다")
    void TC_088_픽업_번호로_주문을_조회할_수_있고_없는_번호는_404다() {
        Long customerId = registerCustomerId();
        String customerToken = issueToken(customerId, Role.CUSTOMER);
        String adminToken = issueToken(registerAdminId(), Role.ADMIN);
        Long orderId = confirmOrder(customerId, customerToken);

        ResponseEntity<AdminOrderDetailResponse> detail = restTemplate.exchange(
                "/api/admin/orders/" + orderId, HttpMethod.GET, new HttpEntity<>(bearer(adminToken)), AdminOrderDetailResponse.class);
        String pickupNumber = detail.getBody().pickupNumber();

        ResponseEntity<AdminOrderDetailResponse> byNumber = restTemplate.exchange(
                "/api/admin/orders/by-pickup-number?pickupNumber=" + Integer.parseInt(pickupNumber), HttpMethod.GET,
                new HttpEntity<>(bearer(adminToken)), AdminOrderDetailResponse.class);
        assertThat(byNumber.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byNumber.getBody().orderId()).isEqualTo(orderId);
        // 06번 SC-109(픽업 번호 조회)가 "픽업 시간대"를 표시한다. API-113은 API-114와 같은
        // 상세 객체를 돌려주므로 이 경로로도 시간대가 채워져야 한다.
        assertThat(byNumber.getBody().pickupStartAt()).isNotNull().isEqualTo(detail.getBody().pickupStartAt());
        assertThat(byNumber.getBody().pickupEndAt()).isNotNull().isEqualTo(detail.getBody().pickupEndAt());

        ResponseEntity<ErrorResponse> notFound = restTemplate.exchange(
                "/api/admin/orders/by-pickup-number?pickupNumber=999", HttpMethod.GET,
                new HttpEntity<>(bearer(adminToken)), ErrorResponse.class);
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("TC_052_픽업_번호는_같은_영업일에_중복되지_않고_취소된_번호도_재사용하지_않는다")
    void TC_052_픽업_번호는_같은_영업일에_중복되지_않고_취소된_번호도_재사용하지_않는다() {
        String adminToken = issueToken(registerAdminId(), Role.ADMIN);

        Long firstCustomerId = registerCustomerId();
        Long firstOrderId = confirmOrder(firstCustomerId, issueToken(firstCustomerId, Role.CUSTOMER));
        Long secondCustomerId = registerCustomerId();
        Long secondOrderId = confirmOrder(secondCustomerId, issueToken(secondCustomerId, Role.CUSTOMER));

        String firstNumber = pickupNumberOf(firstOrderId, adminToken);
        String secondNumber = pickupNumberOf(secondOrderId, adminToken);
        assertThat(firstNumber).isNotNull();
        assertThat(secondNumber).isNotNull().isNotEqualTo(firstNumber);

        // BR-026 — 취소로 비게 된 번호도 되돌려 쓰지 않는다. 매장에서 같은 번호를 두 번
        // 부르는 상황을 막기 위해서다.
        restTemplate.exchange(
                "/api/admin/orders/" + firstOrderId + "/cancel", HttpMethod.POST,
                new HttpEntity<>(new AdminCancelOrderRequest("재고 파손"), bearer(adminToken)), OrderCancelResponse.class);

        Long thirdCustomerId = registerCustomerId();
        Long thirdOrderId = confirmOrder(thirdCustomerId, issueToken(thirdCustomerId, Role.CUSTOMER));
        String thirdNumber = pickupNumberOf(thirdOrderId, adminToken);

        assertThat(thirdNumber).isNotIn(firstNumber, secondNumber);
    }

    private String pickupNumberOf(Long orderId, String adminToken) {
        return restTemplate.exchange(
                        "/api/admin/orders/" + orderId, HttpMethod.GET, new HttpEntity<>(bearer(adminToken)),
                        AdminOrderDetailResponse.class)
                .getBody()
                .pickupNumber();
    }

    @Test
    @DisplayName("TC_090_091_준비완료와_완료처리가_순서대로_동작하고_중복_완료는_거부된다")
    void TC_090_091_준비완료와_완료처리가_순서대로_동작하고_중복_완료는_거부된다() {
        Long customerId = registerCustomerId();
        String customerToken = issueToken(customerId, Role.CUSTOMER);
        String adminToken = issueToken(registerAdminId(), Role.ADMIN);
        Long orderId = confirmOrder(customerId, customerToken);

        ResponseEntity<OrderFulfillResponse> readyResponse = restTemplate.exchange(
                "/api/admin/orders/" + orderId + "/ready", HttpMethod.POST, new HttpEntity<>(bearer(adminToken)), OrderFulfillResponse.class);
        assertThat(readyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readyResponse.getBody().status()).isEqualTo("READY");
        assertThat(readyResponse.getBody().stockChanged()).isFalse();

        ResponseEntity<OrderFulfillResponse> completeResponse = restTemplate.exchange(
                "/api/admin/orders/" + orderId + "/complete", HttpMethod.POST, new HttpEntity<>(bearer(adminToken)), OrderFulfillResponse.class);
        assertThat(completeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completeResponse.getBody().status()).isEqualTo("COMPLETED");

        ResponseEntity<ErrorResponse> duplicateComplete = restTemplate.exchange(
                "/api/admin/orders/" + orderId + "/complete", HttpMethod.POST, new HttpEntity<>(bearer(adminToken)), ErrorResponse.class);
        assertThat(duplicateComplete.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicateComplete.getBody().code()).isEqualTo("INVALID_ORDER_STATUS");
    }

    @Test
    @DisplayName("TC_095_사유_없이_관리자_취소를_요청하면_CANCEL_REASON_REQUIRED다")
    void TC_095_사유_없이_관리자_취소를_요청하면_CANCEL_REASON_REQUIRED다() {
        Long customerId = registerCustomerId();
        String customerToken = issueToken(customerId, Role.CUSTOMER);
        String adminToken = issueToken(registerAdminId(), Role.ADMIN);
        Long orderId = confirmOrder(customerId, customerToken);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/orders/" + orderId + "/cancel", HttpMethod.POST,
                new HttpEntity<>(new AdminCancelOrderRequest(null), bearer(adminToken)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("CANCEL_REASON_REQUIRED");
    }

    @Test
    @DisplayName("TC_096_사유가_있으면_관리자_취소가_실행된다")
    void TC_096_사유가_있으면_관리자_취소가_실행된다() {
        Long customerId = registerCustomerId();
        String customerToken = issueToken(customerId, Role.CUSTOMER);
        String adminToken = issueToken(registerAdminId(), Role.ADMIN);
        Long orderId = confirmOrder(customerId, customerToken);

        ResponseEntity<OrderCancelResponse> response = restTemplate.exchange(
                "/api/admin/orders/" + orderId + "/cancel", HttpMethod.POST,
                new HttpEntity<>(new AdminCancelOrderRequest("상품 이상 발견"), bearer(adminToken)), OrderCancelResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("CANCELED");
        assertThat(response.getBody().canceledBy()).isEqualTo("ADMIN");
        assertThat(response.getBody().cancelReason()).isEqualTo("상품 이상 발견");
    }
}
