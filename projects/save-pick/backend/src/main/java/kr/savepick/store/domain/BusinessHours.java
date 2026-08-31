package kr.savepick.store.domain;

import java.time.LocalTime;

/**
 * BR-014 — 영업시간은 30분 단위이고 시작 시각은 종료 시각보다 앞서야 한다.
 * 값 자체의 유효성 판정만 domain이 맡는다. 위반을 BUSINESS_HOUR_INVALID로 번역하는 것은
 * application 계층의 몫이다 (14-project-structure.md §3.2, account 슬라이스의
 * LoginBlockPolicy와 같은 패턴 — domain은 판정 결과만 돌려주고 예외로 바꾸지 않는다).
 */
public record BusinessHours(LocalTime openTime, LocalTime closeTime) {

    public static boolean isValid(LocalTime openTime, LocalTime closeTime) {
        return openTime != null && closeTime != null
                && openTime.isBefore(closeTime)
                && isHalfHourAligned(openTime)
                && isHalfHourAligned(closeTime);
    }

    private static boolean isHalfHourAligned(LocalTime time) {
        return time.getSecond() == 0 && time.getNano() == 0 && (time.getMinute() == 0 || time.getMinute() == 30);
    }
}
