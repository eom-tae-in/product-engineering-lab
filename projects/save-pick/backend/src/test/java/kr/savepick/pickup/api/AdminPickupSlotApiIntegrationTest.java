package kr.savepick.pickup.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.account.domain.Role;
import kr.savepick.account.infrastructure.BcryptPasswordHasher;
import kr.savepick.account.infrastructure.JwtAccessTokenIssuer;
import kr.savepick.common.response.ErrorResponse;
import kr.savepick.common.time.ServerClock;
import kr.savepick.pickup.application.PickupSlotProvisionService;
import kr.savepick.pickup.application.PickupSlotReserveService;
import kr.savepick.pickup.domain.PickupSlot;
import kr.savepick.pickup.domain.PickupSlotRepository;
import kr.savepick.store.domain.Store;
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
 * API-118·119 (11-api-spec.md §10, docs/16-test-plan.md TC-099·102·103).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class AdminPickupSlotApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PickupSlotProvisionService pickupSlotProvisionService;

    @Autowired
    private PickupSlotRepository pickupSlotRepository;

    @Autowired
    private PickupSlotReserveService pickupSlotReserveService;

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

    @Test
    @DisplayName("TC_099_시간대별_현황을_조회할_수_있다")
    void TC_099_시간대별_현황을_조회할_수_있다() {
        LocalDate date = LocalDate.of(2026, 10, 1);
        pickupSlotProvisionService.provisionForDate(date, serverClock.now());
        String adminToken = issueAdminToken();

        ResponseEntity<AdminPickupSlotOverviewResponse> response = restTemplate.exchange(
                "/api/admin/pickup-slots?date=" + date, HttpMethod.GET, new HttpEntity<>(bearer(adminToken)),
                AdminPickupSlotOverviewResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().slots()).hasSize(24);
        assertThat(response.getBody().slots().get(0).itemTotals()).isEmpty();
    }

    @Test
    @DisplayName("고객_토큰으로_조회하면_403이다")
    void 고객_토큰으로_조회하면_403이다() {
        String customerToken = issueCustomerToken();

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/pickup-slots?date=2026-10-01", HttpMethod.GET, new HttpEntity<>(bearer(customerToken)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("TC_102_정원을_축소해도_기존_예약은_취소되지_않고_overCapacity로_표시된다")
    void TC_102_정원을_축소해도_기존_예약은_취소되지_않고_overCapacity로_표시된다() {
        PickupSlot slot = createSlotWithReserved((short) 20, (short) 18);
        String adminToken = issueAdminToken();

        ResponseEntity<UpdatePickupSlotResponse> response = restTemplate.exchange(
                "/api/admin/pickup-slots/" + slot.getId(), HttpMethod.PATCH,
                new HttpEntity<>(new UpdatePickupSlotRequest((short) 12, null), bearer(adminToken)), UpdatePickupSlotResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().capacity()).isEqualTo(12);
        assertThat(response.getBody().reservedCount()).isEqualTo(18);
        assertThat(response.getBody().overCapacity()).isTrue();
    }

    @Test
    @DisplayName("TC_103_차단_후_해제하면_다시_선택_가능한_상태로_돌아간다")
    void TC_103_차단_후_해제하면_다시_선택_가능한_상태로_돌아간다() {
        PickupSlot slot = createSlotWithReserved((short) 20, (short) 0);
        String adminToken = issueAdminToken();

        ResponseEntity<UpdatePickupSlotResponse> blockResponse = restTemplate.exchange(
                "/api/admin/pickup-slots/" + slot.getId(), HttpMethod.PATCH,
                new HttpEntity<>(new UpdatePickupSlotRequest(null, true), bearer(adminToken)), UpdatePickupSlotResponse.class);
        assertThat(blockResponse.getBody()).isNotNull();
        assertThat(blockResponse.getBody().blocked()).isTrue();

        ResponseEntity<UpdatePickupSlotResponse> unblockResponse = restTemplate.exchange(
                "/api/admin/pickup-slots/" + slot.getId(), HttpMethod.PATCH,
                new HttpEntity<>(new UpdatePickupSlotRequest(null, false), bearer(adminToken)), UpdatePickupSlotResponse.class);
        assertThat(unblockResponse.getBody()).isNotNull();
        assertThat(unblockResponse.getBody().blocked()).isFalse();
    }

    @Test
    @DisplayName("존재하지_않는_시간대_변경은_404다")
    void 존재하지_않는_시간대_변경은_404다() {
        String adminToken = issueAdminToken();

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/pickup-slots/999999999", HttpMethod.PATCH,
                new HttpEntity<>(new UpdatePickupSlotRequest((short) 5, null), bearer(adminToken)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("SLOT_NOT_FOUND");
    }

    @Test
    @DisplayName("정원을_0으로_바꾸려_하면_VALIDATION_ERROR다")
    void 정원을_0으로_바꾸려_하면_VALIDATION_ERROR다() {
        PickupSlot slot = createSlotWithReserved((short) 20, (short) 0);
        String adminToken = issueAdminToken();

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/pickup-slots/" + slot.getId(), HttpMethod.PATCH,
                new HttpEntity<>(new UpdatePickupSlotRequest((short) 0, null), bearer(adminToken)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    /** 이 클래스는 클래스 레벨 {@code @Transactional}이 없어(HTTP 왕복 필요) 생성한 슬롯이 테스트
     * 간에 롤백되지 않는다 — {@code UNIQUE(store_id, start_at)}와 부딪히지 않도록 호출마다
     * 다른 시작 시각을 쓴다. */
    private static final java.util.concurrent.atomic.AtomicInteger SLOT_OFFSET = new java.util.concurrent.atomic.AtomicInteger();

    private PickupSlot createSlotWithReserved(short capacity, short reservedCount) {
        var now = serverClock.now();
        var date = now.toLocalDate().plusDays(1);
        var start = date.atTime(0, 0).plusMinutes(30L * SLOT_OFFSET.incrementAndGet());
        PickupSlot slot = PickupSlot.create(Store.SINGLETON_ID, date, start, start.plusMinutes(30), capacity, now);
        slot = pickupSlotRepository.save(slot);
        for (int i = 0; i < reservedCount; i++) {
            pickupSlotReserveService.occupy(slot.getId());
        }
        return pickupSlotRepository.findById(slot.getId()).orElseThrow();
    }
}
