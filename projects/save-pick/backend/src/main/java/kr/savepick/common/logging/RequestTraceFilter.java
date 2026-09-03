package kr.savepick.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 14-project-structure.md §9.5 — 요청마다 {@code traceId}를 발급해 MDC에 넣고, 처리가 끝나면
 * 한 줄 요약을 남긴다.
 *
 * <p>클라이언트나 앞단 프록시가 {@code X-Request-Id}를 주면 그 값을 이어 쓴다. 여러 서버를
 * 거친 요청을 하나로 묶어 보려면 밖에서 온 식별자를 버리지 않아야 한다. 길이를 제한하고
 * 허용 문자만 남기는 이유는 이 값이 모든 로그 줄에 그대로 찍히기 때문이다 — 로그 주입을
 * 막는다.
 *
 * <p><b>요청 본문·쿼리스트링·헤더는 남기지 않는다.</b> 12번 §5 P4는 {@code password},
 * {@code accessToken}, {@code Set-Cookie}를 마스킹하라고 하는데, 애초에 기록하지 않으면
 * 마스킹을 빠뜨릴 자리도 없다. 장애를 좇는 데 필요한 것은 "어느 요청이 몇 ms 걸려 몇 번으로
 * 끝났는가"이지 본문이 아니다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestTraceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);

    /** 로그 패턴(logback-spring.xml)이 읽는 키. */
    public static final String TRACE_ID = "traceId";

    static final String TRACE_HEADER = "X-Request-Id";
    static final int MAX_TRACE_ID_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(TRACE_HEADER));
        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_HEADER, traceId);

        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("{} {} -> {} ({}ms)",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMillis);
            MDC.remove(TRACE_ID);
        }
    }

    /**
     * 밖에서 온 값은 영숫자·하이픈만 남기고 잘라 쓴다. 쓸 수 있는 문자가 하나도 없으면 새로 만든다.
     */
    static String resolveTraceId(String incoming) {
        if (incoming == null) {
            return newTraceId();
        }
        String sanitized = incoming.replaceAll("[^A-Za-z0-9-]", "");
        if (sanitized.isEmpty()) {
            return newTraceId();
        }
        return sanitized.length() > MAX_TRACE_ID_LENGTH ? sanitized.substring(0, MAX_TRACE_ID_LENGTH) : sanitized;
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * actuator 헬스 체크는 로드밸런서가 초 단위로 찔러 로그를 가득 채운다. 추적할 사용자 요청이
     * 아니므로 거른다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }
}
