package kr.savepick.account.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import kr.savepick.common.error.ErrorCode;
import kr.savepick.common.response.ErrorResponse;
import kr.savepick.common.time.ServerClock;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 12-auth.md §3.2 — 권한 부족은 항상 403 FORBIDDEN, 어떤 리소스 데이터도 담지 않는다.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final ServerClock serverClock;

    public RestAccessDeniedHandler(ObjectMapper objectMapper, ServerClock serverClock) {
        this.objectMapper = objectMapper;
        this.serverClock = serverClock;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        ErrorCode code = ErrorCode.FORBIDDEN;
        response.setStatus(code.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = new ErrorResponse(
                code.name(), code.defaultMessage(),
                serverClock.now().atZone(serverClock.zone()).toOffsetDateTime(), Map.of());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
