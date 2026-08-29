package kr.savepick.common.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 애플리케이션 전역 {@link Clock} 빈 (BR-028, savepick.time-zone).
 * kr.savepick.common.time.ServerClock이 이 빈을 주입받아 시각의 단일 출처가 된다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${savepick.time-zone:Asia/Seoul}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
