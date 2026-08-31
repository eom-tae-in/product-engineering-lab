package kr.savepick.common.api;

import kr.savepick.common.time.ServerClock;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-008 서버 시각 조회 (11-api-spec.md §1, FR-005, BR-028) — 인증 불필요.
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final ServerClock serverClock;

    public SystemController(ServerClock serverClock) {
        this.serverClock = serverClock;
    }

    @GetMapping("/time")
    public SystemTimeResponse getTime() {
        return new SystemTimeResponse(
                serverClock.now().atZone(serverClock.zone()).toOffsetDateTime(),
                serverClock.zone().getId());
    }

    public record SystemTimeResponse(java.time.OffsetDateTime serverTime, String timezone) {
    }
}
