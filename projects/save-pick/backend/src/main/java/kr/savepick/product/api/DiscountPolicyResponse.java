package kr.savepick.product.api;

import java.util.List;

/**
 * API-108. BR-004 할인 구간표를 그대로 옮긴 사실상 정적 응답 (11-api-spec.md API-108 예시).
 */
public record DiscountPolicyResponse(
        List<Tier> tiers, String rounding, int minimumPrice, String boundaryRule, boolean editable) {

    public static DiscountPolicyResponse standard() {
        return new DiscountPolicyResponse(
                List.of(
                        new Tier("D0", null, null, "24시간 초과", 0),
                        new Tier("D1", null, 24, "24시간 이하 ~ 6시간 초과", 30),
                        new Tier("D2", null, 6, "6시간 이하 ~ 2시간 초과", 50),
                        new Tier("D3", null, 2, "2시간 이하 ~ 마감 이전", 70)),
                "10원 단위 내림",
                100,
                "경계값은 더 큰 할인율 구간에 속한다",
                false);
    }

    public record Tier(String code, Integer remainingFrom, Integer remainingToHours, String condition, int discountRate) {
    }
}
