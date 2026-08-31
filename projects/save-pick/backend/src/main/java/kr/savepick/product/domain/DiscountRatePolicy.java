package kr.savepick.product.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * BR-004 — 마감까지 남은 시간으로 할인율을 자동 결정한다. 관리자가 개별 상품의 할인율을 수동
 * 지정하지 않는다. 순수 도메인 정책이므로 Clock을 직접 호출하지 않고 now를 인자로 받는다
 * (14-project-structure.md §9.1, account의 LoginBlockPolicy·store의 BusinessHours와 같은 패턴).
 */
public final class DiscountRatePolicy {

    private static final Duration TIER1_THRESHOLD = Duration.ofHours(24);
    private static final Duration TIER2_THRESHOLD = Duration.ofHours(6);
    private static final Duration TIER3_THRESHOLD = Duration.ofHours(2);

    private static final int MINIMUM_PRICE = 100;

    private DiscountRatePolicy() {
    }

    /** 마감 시각에 이미 도달했는지(판매 종료 여부, BR-030) 판정한다. */
    public static boolean isClosed(LocalDateTime closingAt, LocalDateTime now) {
        return !now.isBefore(closingAt);
    }

    /**
     * 할인율(%)을 계산한다. 마감 시각에 이미 도달한 경우 호출하지 않는다 — 그 판정은
     * {@link #isClosed(LocalDateTime, LocalDateTime)}로 먼저 한다 (BR-030).
     */
    public static int discountRate(LocalDateTime closingAt, LocalDateTime now) {
        if (isClosed(closingAt, now)) {
            throw new IllegalStateException("마감된 상품의 할인율은 계산하지 않는다 — 먼저 isClosed로 판정한다.");
        }
        Duration remaining = Duration.between(now, closingAt);
        if (remaining.compareTo(TIER1_THRESHOLD) > 0) {
            return 0;
        }
        if (remaining.compareTo(TIER2_THRESHOLD) > 0) {
            return 30;
        }
        if (remaining.compareTo(TIER3_THRESHOLD) > 0) {
            return 50;
        }
        return 70;
    }

    /**
     * 할인가 = 내림(정가 × (1 − 할인율) ÷ 10) × 10. 100원 미만이면 100원으로 한다.
     * 정수 연산만으로 부동소수점 오차 없이 계산한다.
     */
    public static int discountPrice(int originalPrice, int discountRate) {
        long floored = Math.floorDiv((long) originalPrice * (100 - discountRate), 1000) * 10;
        return (int) Math.max(MINIMUM_PRICE, floored);
    }

    /**
     * 다음 할인 구간과 적용 시각. 이미 최대 할인 구간(70%)이면 더 이상 다음 구간이 없다.
     */
    public static Optional<NextDiscount> nextDiscount(LocalDateTime closingAt, LocalDateTime now) {
        if (isClosed(closingAt, now)) {
            return Optional.empty();
        }
        int currentRate = discountRate(closingAt, now);
        return switch (currentRate) {
            case 0 -> Optional.of(new NextDiscount(30, closingAt.minus(TIER1_THRESHOLD)));
            case 30 -> Optional.of(new NextDiscount(50, closingAt.minus(TIER2_THRESHOLD)));
            case 50 -> Optional.of(new NextDiscount(70, closingAt.minus(TIER3_THRESHOLD)));
            default -> Optional.empty();
        };
    }

    public record NextDiscount(int rate, LocalDateTime at) {
    }
}
