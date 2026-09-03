package kr.savepick.common.dev;

import java.time.LocalDateTime;
import java.time.LocalTime;
import kr.savepick.account.domain.Member;
import kr.savepick.account.domain.MemberRepository;
import kr.savepick.account.infrastructure.BcryptPasswordHasher;
import kr.savepick.common.time.ServerClock;
import kr.savepick.product.application.ProductRegisterService;
import kr.savepick.product.application.ProductStatusService;
import kr.savepick.product.domain.Product;
import kr.savepick.product.domain.ProductStatus;
import kr.savepick.stock.application.StockAdjustService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬 개발·데모용 초기 데이터를 넣는다 ({@code dev} 프로파일 전용).
 *
 * <p>관리자 계정을 만드는 유일한 경로다 — 12-auth.md §5 P7이 "관리자 계정은 API로 만들지
 * 않는다(운영 스크립트·마이그레이션 전용)"고 정해 두어 가입 API가 없고, 시드가 없으면
 * 관리자 화면(SC-101~113)에 아예 로그인할 수 없다.
 *
 * <p>상품·재고는 도메인 서비스를 그대로 호출해 만든다. SQL로 직접 넣지 않는 이유는 재고
 * 원장(stock_ledgers)·상품 변경 이력 같은 부수 기록이 실제 운영과 같은 모양으로 쌓여야
 * 재고 이력(SC-106) 화면도 빈 화면이 아니게 되기 때문이다.
 *
 * <p>픽업 시간대는 기동 시 BATCH-05({@code PickupSlotProvisionJob})가 D+0·D+1에 자동으로
 * 만들므로 여기서 만들지 않는다.
 *
 * <p>이미 시드된 DB에서 다시 떠도 안전하다 — 관리자 계정 존재 여부로 한 번만 실행한다.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private static final String ADMIN_EMAIL = "admin@savepick.kr";
    private static final String ADMIN_PASSWORD = "adminpass1";
    private static final String CUSTOMER_EMAIL = "customer@savepick.kr";
    private static final String CUSTOMER_PASSWORD = "savepick123";

    /** 매장 영업 종료 시각(10:00~22:00, G1 확정값). 마감 시각은 이 시각을 넘길 수 없다(BR-003). */
    private static final LocalTime STORE_CLOSE_TIME = LocalTime.of(22, 0);

    private final MemberRepository memberRepository;
    private final BcryptPasswordHasher passwordHasher;
    private final ProductRegisterService productRegisterService;
    private final ProductStatusService productStatusService;
    private final StockAdjustService stockAdjustService;
    private final ServerClock serverClock;

    public DevDataSeeder(
            MemberRepository memberRepository,
            BcryptPasswordHasher passwordHasher,
            ProductRegisterService productRegisterService,
            ProductStatusService productStatusService,
            StockAdjustService stockAdjustService,
            ServerClock serverClock) {
        this.memberRepository = memberRepository;
        this.passwordHasher = passwordHasher;
        this.productRegisterService = productRegisterService;
        this.productStatusService = productStatusService;
        this.stockAdjustService = stockAdjustService;
        this.serverClock = serverClock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (memberRepository.existsByEmail(ADMIN_EMAIL)) {
            log.info("[dev] 데모 데이터가 이미 있어 시드를 건너뜁니다 (관리자 {}).", ADMIN_EMAIL);
            return;
        }

        LocalDateTime now = serverClock.now();
        Long adminId = saveMember(Member.registerAdmin(
                ADMIN_EMAIL, passwordHasher.hash(ADMIN_PASSWORD), "매장 관리자", "01099998888", now));
        saveMember(Member.registerCustomer(
                CUSTOMER_EMAIL, passwordHasher.hash(CUSTOMER_PASSWORD), "김지현", "01011112222", now));

        // 마감까지 남은 시간을 다르게 둬 할인 구간(24h/6h/2h 경계 → 0·30·50·70%)이 한 화면에서
        // 모두 보이게 한다(BR-004).
        seedProduct("국내산 삼겹살 300g", "오늘 손질한 국내산 삼겹살입니다.", "300g", 12000, 1, (short) 5, 8, adminId);
        seedProduct("유기농 시금치 한 단", "아침에 들어온 유기농 시금치입니다.", "1단", 4500, 4, (short) 5, 12, adminId);
        seedProduct("모닝빵 6개입", "매일 아침 구운 모닝빵입니다.", "6개입", 6000, 8, (short) 3, 5, adminId);
        seedProduct("제주 감귤 1.5kg", "제주에서 올라온 감귤입니다.", "1.5kg", 15000, 30, (short) 2, 20, adminId);
        seedProduct("훈제 오리 슬라이스", "간편하게 데워 먹는 훈제 오리입니다.", "400g", 13000, 5, (short) 4, 0, adminId);

        log.info("[dev] 데모 데이터를 넣었습니다 — 관리자 {} / {}, 고객 {} / {}",
                ADMIN_EMAIL, ADMIN_PASSWORD, CUSTOMER_EMAIL, CUSTOMER_PASSWORD);
    }

    private Long saveMember(Member member) {
        return memberRepository.save(member).getId();
    }

    /**
     * 상품을 등록하고 재고를 넣은 뒤 판매 중으로 전환한다.
     *
     * <p>{@code stockQuantity}가 0이어도 먼저 1개를 넣고 판매를 시작한 뒤 0으로 내린다 —
     * DRAFT → ON_SALE 전이는 재고가 1개 이상 등록돼 있을 것을 요구하기 때문이다
     * (BR-005, {@code StockQueryService.isStockRegistered}). 실제 운영에서도 품절 상품은
     * 이 경로(판매 시작 후 재고 소진)로만 생긴다. 품절 상품은 SC-001 "품절 숨기기"·
     * SC-105 "판매 가능 0" 필터를 확인하는 데 쓴다.
     */
    private void seedProduct(
            String name, String description, String saleUnit, int originalPrice,
            int closingInHours, short maxOrderQuantity, int stockQuantity, Long adminId) {
        LocalDateTime now = serverClock.now();
        Product product = productRegisterService.register(
                name, description, saleUnit, originalPrice, closingAt(now, closingInHours), maxOrderQuantity, adminId);
        stockAdjustService.adjust(product.getId(), Math.max(stockQuantity, 1), "데모 초기 재고", adminId);
        productStatusService.changeStatus(product.getId(), ProductStatus.ON_SALE, adminId);
        if (stockQuantity == 0) {
            stockAdjustService.adjust(product.getId(), 0, "데모 품절 처리", adminId);
        }
    }

    /**
     * BR-003 — 마감 시각은 미래여야 하고 시각(시:분)이 영업 종료 시각을 넘을 수 없다.
     * 요청한 시간을 더했을 때 날짜가 바뀌거나 영업 종료를 넘기면 다음 날 20:00으로 접는다
     * (테스트의 {@code ProductTestFixtures.futureClosingAt}과 같은 규칙).
     */
    private LocalDateTime closingAt(LocalDateTime now, int hoursAhead) {
        LocalDateTime candidate = now.plusHours(hoursAhead);
        boolean crossedIntoNextDay = !candidate.toLocalDate().equals(now.toLocalDate());
        boolean afterStoreClose = candidate.toLocalTime().isAfter(STORE_CLOSE_TIME);
        if (crossedIntoNextDay || afterStoreClose) {
            return now.toLocalDate().plusDays(1).atTime(20, 0);
        }
        return candidate;
    }
}
