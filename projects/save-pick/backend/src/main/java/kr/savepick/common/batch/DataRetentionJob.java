package kr.savepick.common.batch;

import java.time.LocalDateTime;
import java.util.function.IntUnaryOperator;
import kr.savepick.account.application.AuthSessionCleanupService;
import kr.savepick.account.application.LoginAttemptCleanupService;
import kr.savepick.cart.application.GuestCartCleanupService;
import kr.savepick.common.time.ServerClock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * BATCH-06 만료 데이터 정리 — 매일 04:00 (11-api-spec.md §11, 10-erd.md §8, FR-002, BR-002).
 * 만료 세션·보관 기간이 지난 로그인 시도 기록·오래된 게스트 장바구니를 지운다.
 *
 * <p><b>주문·재고 원장은 삭제하지 않는다</b> — {@code orders}, {@code stock_ledgers},
 * {@code order_status_histories}, {@code inventory_holds}는 지표 집계와 감사 근거라 10-erd.md
 * §8이 명시적으로 보존 대상으로 정해 두었다. 그래서 이 배치는 그 테이블들을 아예 참조하지
 * 않는다.
 *
 * <p>여러 도메인에 걸친 위생 작업이라 {@code common/batch}에 두지만, 실제 삭제는 각 도메인의
 * 정리 서비스가 한다(14-project-structure.md §6.1·6.2 — 배치는 보관 기간을 계산하지도, 삭제
 * 조건을 판정하지도 않는다). 보관 기간은 각 서비스가 {@code savepick.retention.*} 설정에서
 * 읽는다.
 *
 * <p>한 덩어리({@link #CHUNK_SIZE}건)씩 반복 호출해 삭제 트랜잭션을 짧게 유지한다
 * (13-inventory-concurrency.md §7.2 — 많은 행을 한 트랜잭션에 묶지 않는다). 한 번 실행에서
 * {@link #MAX_CHUNKS}덩어리까지만 처리하고 남은 행은 다음 날 실행이 이어서 지운다.
 *
 * <p>{@code test} 프로파일에서는 비활성화한다(다른 배치와 같은 이유 — 실제 시각 기준 실행이
 * 통합 테스트 데이터와 경합하지 않게 한다). 테스트는 각 정리 서비스를 직접 호출해 검증한다.
 */
@Component
@Profile("!test")
public class DataRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionJob.class);
    private static final int CHUNK_SIZE = 500;
    private static final int MAX_CHUNKS = 100;

    private final AuthSessionCleanupService authSessionCleanupService;
    private final LoginAttemptCleanupService loginAttemptCleanupService;
    private final GuestCartCleanupService guestCartCleanupService;
    private final ServerClock serverClock;

    public DataRetentionJob(
            AuthSessionCleanupService authSessionCleanupService,
            LoginAttemptCleanupService loginAttemptCleanupService,
            GuestCartCleanupService guestCartCleanupService,
            ServerClock serverClock) {
        this.authSessionCleanupService = authSessionCleanupService;
        this.loginAttemptCleanupService = loginAttemptCleanupService;
        this.guestCartCleanupService = guestCartCleanupService;
        this.serverClock = serverClock;
    }

    @Scheduled(cron = "${savepick.batch.data-retention.cron}", zone = "${savepick.time-zone}")
    @SchedulerLock(name = "BATCH-06-data-retention", lockAtMostFor = "PT30M")
    public void run() {
        LocalDateTime now = serverClock.now();

        int sessions = deleteInChunks("만료 세션", chunkSize -> authSessionCleanupService.deleteExpiredSessions(now, chunkSize));
        int loginAttempts = deleteInChunks("로그인 시도 기록", chunkSize -> loginAttemptCleanupService.deleteOldAttempts(now, chunkSize));
        int guestCarts = deleteInChunks("게스트 장바구니", chunkSize -> guestCartCleanupService.deleteStaleGuestCarts(now, chunkSize));

        log.info(
                "BATCH-06 만료 데이터 정리 완료 — 만료 세션 {}건, 로그인 시도 기록 {}건, 게스트 장바구니 {}건 삭제 "
                        + "(주문·재고 원장은 삭제 대상이 아니다)",
                sessions, loginAttempts, guestCarts);
    }

    /** 더 지울 것이 없을 때까지(덩어리가 가득 차지 않을 때까지) 정리 서비스를 반복 호출한다. */
    private int deleteInChunks(String target, IntUnaryOperator chunkDeleter) {
        int total = 0;
        for (int chunk = 0; chunk < MAX_CHUNKS; chunk++) {
            int deleted = chunkDeleter.applyAsInt(CHUNK_SIZE);
            total += deleted;
            if (deleted < CHUNK_SIZE) {
                return total;
            }
        }
        log.warn("BATCH-06 {} 정리가 한 번 실행 상한({}건)에 도달했다 — 남은 행은 다음 실행에서 이어서 지운다",
                target, MAX_CHUNKS * CHUNK_SIZE);
        return total;
    }
}
