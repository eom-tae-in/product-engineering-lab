package kr.savepick.common.time;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * 서버 시각의 단일 출처 (BR-028).
 * 도메인·애플리케이션 코드는 {@link LocalDateTime#now()}를 직접 호출하지 않고
 * 이 클래스를 통해 시각을 얻는다. 테스트에서는 {@link Clock}을 고정해 주입한다.
 */
@Component
public class ServerClock {

    private final Clock clock;

    public ServerClock(Clock clock) {
        this.clock = clock;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public ZoneId zone() {
        return clock.getZone();
    }
}
