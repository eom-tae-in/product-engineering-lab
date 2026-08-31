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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 12-auth.md §3.2 — 인증 실패는 항상 401 UNAUTHENTICATED, 11번 §0.3 형식으로 응답한다.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final ServerClock serverClock;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper, ServerClock serverClock) {
        this.objectMapper = objectMapper;
        this.serverClock = serverClock;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        ErrorCode code = ErrorCode.UNAUTHENTICATED;
        response.setStatus(code.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = new ErrorResponse(
                code.name(), code.defaultMessage(),
                serverClock.now().atZone(serverClock.zone()).toOffsetDateTime(), Map.of());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
