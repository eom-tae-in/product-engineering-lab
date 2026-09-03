package kr.savepick.common.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import kr.savepick.account.application.AuthSessionCleanupService;
import kr.savepick.account.application.LoginAttemptCleanupService;
import kr.savepick.account.domain.AuthSession;
import kr.savepick.account.domain.LoginAttempt;
import kr.savepick.account.domain.LoginAttemptRepository;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.account.infrastructure.AuthSessionJpaRepository;
import kr.savepick.cart.application.GuestCartCleanupService;
import kr.savepick.cart.domain.Cart;
import kr.savepick.cart.domain.CartItem;
import kr.savepick.cart.domain.CartItemRepository;
import kr.savepick.cart.domain.CartRepository;
import kr.savepick.common.audit.ActorType;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.domain.Product;
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
 * BATCH-06 만료 데이터 정리 (11-api-spec.md §11, 10-erd.md §8, FR-002).
 * 배치({@code common/batch/DataRetentionJob})는 {@code @Profile("!test")}라 테스트에서 로드되지
 * 않으므로, 배치가 호출하는 세 도메인의 정리 서비스를 직접 호출해 검증한다.
 * 여러 도메인에 걸친 하나의 위생 계약이라 배치와 같은 자리({@code common/batch})에서 함께 본다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class DataRetentionIntegrationTest {

    private static final int CHUNK_SIZE = 500;

    @Autowired
    private AuthSessionCleanupService authSessionCleanupService;

    @Autowired
    private LoginAttemptCleanupService loginAttemptCleanupService;

    @Autowired
    private GuestCartCleanupService guestCartCleanupService;

    @Autowired
    private AuthSessionJpaRepository authSessionJpaRepository;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRegisterService productRegisterService;

    @Autowired
    private StockAdjustService stockAdjustService;

    @Autowired
    private InventoryHoldService inventoryHoldService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ServerClock serverClock;

    private Long registerMember(String prefix) {
        Member member = "admin".equals(prefix)
                ? Member.registerAdmin(prefix + "-" + UUID.randomUUID() + "@test.com", "hash", "관리자", "01000000000", serverClock.now())
                : Member.registerCustomer(prefix + "-" + UUID.randomUUID() + "@test.com", "hash", "고객", "01011112222", serverClock.now());
        return memberRepository.save(member).getId();
    }

    private UUID saveSession(Long memberId, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        String tokenHash = UUID.randomUUID().toString().replace("-", "").repeat(2);
        AuthSession session = AuthSession.issue(memberId, tokenHash, issuedAt, expiresAt, "test-agent");
        return authSessionJpaRepository.save(session).getId();
    }

    private Long saveGuestCart(LocalDateTime updatedAt) {
        return cartRepository.save(Cart.forGuest(UUID.randomUUID(), updatedAt)).getId();
    }

    private long countLoginAttempts(String email) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM login_attempts WHERE email = ?", Long.class, email);
    }

    private long countCarts(Long cartId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM carts WHERE id = ?", Long.class, cartId);
    }

    private long countCartItems(Long cartId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM cart_items WHERE cart_id = ?", Long.class, cartId);
    }

    @Test
    @DisplayName("보관_기간이_지난_만료_세션만_지우고_최근_만료본과_유효_세션은_남긴다")
    void 보관_기간이_지난_만료_세션만_지우고_최근_만료본과_유효_세션은_남긴다() {
        LocalDateTime now = serverClock.now();
        Long memberId = registerMember("customer");
        UUID longExpired = saveSession(memberId, now.minusDays(38), now.minusDays(8));
        UUID recentlyExpired = saveSession(memberId, now.minusDays(31), now.minusDays(1));
        UUID live = saveSession(memberId, now, now.plusDays(30));

        int deleted = authSessionCleanupService.deleteExpiredSessions(now, CHUNK_SIZE);

        assertThat(deleted).isEqualTo(1);
        assertThat(authSessionJpaRepository.findById(longExpired)).isEmpty();
        assertThat(authSessionJpaRepository.findById(recentlyExpired)).isPresent();
        assertThat(authSessionJpaRepository.findById(live)).isPresent();
    }

    @Test
    @DisplayName("90일이_지난_로그인_시도_기록만_지운다")
    void 로그인_시도_기록은_90일이_지난_것만_지운다() {
        LocalDateTime now = serverClock.now();
        String oldEmail = "old-" + UUID.randomUUID() + "@test.com";
        String recentEmail = "recent-" + UUID.randomUUID() + "@test.com";
        loginAttemptRepository.save(LoginAttempt.record(oldEmail, null, false, now.minusDays(91)));
        loginAttemptRepository.save(LoginAttempt.record(recentEmail, null, false, now.minusDays(89)));

        int deleted = loginAttemptCleanupService.deleteOldAttempts(now, CHUNK_SIZE);

        assertThat(deleted).isEqualTo(1);
        assertThat(countLoginAttempts(oldEmail)).isZero();
        assertThat(countLoginAttempts(recentEmail)).isEqualTo(1);
    }

    @Test
    @DisplayName("7일이_지난_게스트_장바구니만_담긴_품목과_함께_지우고_회원_장바구니는_남긴다")
    void 게스트_장바구니는_7일이_지난_것만_담긴_품목과_함께_지우고_회원_장바구니는_남긴다() {
        LocalDateTime now = serverClock.now();
        Product product = productRegisterService.register(
                "상품", "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, 1L);
        Long staleGuestCartId = saveGuestCart(now.minusDays(8));
        cartItemRepository.save(CartItem.add(staleGuestCartId, product.getId(), (short) 2, 10000, now.minusDays(8)));
        Long freshGuestCartId = saveGuestCart(now.minusDays(6));
        Long memberCartId = cartRepository.save(Cart.forMember(registerMember("customer"), now.minusDays(30))).getId();

        int deleted = guestCartCleanupService.deleteStaleGuestCarts(now, CHUNK_SIZE);

        assertThat(deleted).isEqualTo(1);
        assertThat(countCarts(staleGuestCartId)).isZero();
        assertThat(countCartItems(staleGuestCartId)).isZero();
        assertThat(countCarts(freshGuestCartId)).isEqualTo(1);
        assertThat(countCarts(memberCartId)).isEqualTo(1);
    }

    @Test
    @DisplayName("정리를_모두_돌려도_주문과_재고_원장은_삭제하지_않는다")
    void 정리를_모두_돌려도_주문과_재고_원장은_삭제하지_않는다() {
        LocalDateTime now = serverClock.now();
        Product product = productRegisterService.register(
                "상품", "설명", "1개", 10000, ProductTestFixtures.futureClosingAt(now, 5), (short) 5, 1L);
        stockAdjustService.adjust(product.getId(), 10, "초기 등록", registerMember("admin"));
        Long customerId = registerMember("customer");
        String orderNo = "ORD-" + String.format("%015d", System.nanoTime() % 1_000_000_000_000_000L);
        Long orderId = jdbcTemplate.queryForObject(
                "INSERT INTO orders (order_no, member_id, status, total_amount, contact_name, contact_phone, hold_expires_at, created_at) "
                        + "VALUES (?, ?, 'PENDING', 0, '테스트', '01000000000', ?, ?) RETURNING id",
                Long.class, orderNo, customerId,
                java.sql.Timestamp.valueOf(now.plusMinutes(10)), java.sql.Timestamp.valueOf(now.minusDays(400)));
        inventoryHoldService.createHolds(orderId, Map.of(product.getId(), 3), ActorType.CUSTOMER, customerId);

        authSessionCleanupService.deleteExpiredSessions(now, CHUNK_SIZE);
        loginAttemptCleanupService.deleteOldAttempts(now, CHUNK_SIZE);
        guestCartCleanupService.deleteStaleGuestCarts(now, CHUNK_SIZE);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders WHERE id = ?", Long.class, orderId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM stock_ledgers WHERE product_id = ?", Long.class, product.getId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM inventory_holds WHERE order_id = ?", Long.class, orderId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM product_stocks WHERE product_id = ?", Long.class, product.getId())).isEqualTo(1);
    }
}
