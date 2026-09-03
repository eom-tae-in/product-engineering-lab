package kr.savepick.cart.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import kr.savepick.cart.domain.CartRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BATCH-06이 호출하는 cart 도메인의 정리 서비스 — 보관 기간이 지난 게스트 장바구니 삭제
 * (11-api-spec.md §11, 10-erd.md §8 "{@code updated_at < now() - 7일}인 {@code guest_token}
 * 장바구니", T-A3).
 *
 * <p>회원 장바구니는 지우지 않는다 — 게스트 토큰은 브라우저를 지우면 되찾을 수 없어 오래된
 * 행이 영구히 남지만, 회원 장바구니는 다시 로그인하면 그대로 이어져야 한다(02 A2).
 * 장바구니는 재고를 점유하지 않으므로(BR-010) 삭제가 재고에 영향을 주지 않는다.
 */
@Service
public class GuestCartCleanupService {

    private final CartRepository cartRepository;
    private final Duration retention;

    public GuestCartCleanupService(
            CartRepository cartRepository,
            @Value("${savepick.retention.guest-cart}") String retention) {
        this.cartRepository = cartRepository;
        this.retention = Duration.parse(retention);
    }

    /**
     * 한 번 호출에 최대 {@code chunkSize}건을 지우고 그 자체로 트랜잭션 하나가 된다
     * (13번 §7.2). 담긴 품목({@code cart_items})은 FK의 ON DELETE CASCADE로 함께 지워진다.
     *
     * @return 이 호출에서 실제로 삭제한 장바구니 수
     */
    @Transactional
    public int deleteStaleGuestCarts(LocalDateTime now, int chunkSize) {
        LocalDateTime threshold = now.minus(retention);
        List<Long> ids = cartRepository.findGuestCartIdsUpdatedBefore(threshold, chunkSize);
        if (ids.isEmpty()) {
            return 0;
        }
        return cartRepository.deleteByIdIn(ids);
    }
}
