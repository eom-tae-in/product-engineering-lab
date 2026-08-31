package kr.savepick.common.error;

import java.util.LinkedHashMap;
import java.util.Map;
import kr.savepick.common.response.ErrorResponse;
import kr.savepick.common.time.ServerClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * domain·application의 BusinessException과 요청 검증 실패를
 * 11-api-spec.md §0.3 형식의 ErrorResponse로 변환한다 (14-project-structure.md §9.3).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ServerClock serverClock;

    public GlobalExceptionHandler(ServerClock serverClock) {
        this.serverClock = serverClock;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.errorCode();
        return ResponseEntity.status(code.httpStatus())
                .body(new ErrorResponse(code.name(), ex.getMessage(), nowOffset(), ex.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(code.httpStatus())
                .body(new ErrorResponse(code.name(), code.defaultMessage(), nowOffset(), Map.of("fields", fieldErrors)));
    }

    /**
     * JSON 파싱 실패(형식이 맞지 않는 날짜·시각 등)도 형식 오류이므로 VALIDATION_ERROR로 통일한다
     * (11-api-spec.md §0.5 — "입력 형식·필수값 위반").
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedRequestBody(HttpMessageNotReadableException ex) {
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(code.httpStatus())
                .body(new ErrorResponse(code.name(), code.defaultMessage(), nowOffset(), Map.of()));
    }

    /**
     * {@code @PreAuthorize} 권한 부족(12-auth.md §3.2)은 경로 기반 필터(SecurityConfig의
     * {@code /api/admin/**} 규칙)가 아니라 컨트롤러 메서드 호출 안에서 판정된다. 그 판정 실패는
     * {@code DispatcherServlet}의 디스패치 도중 발생해 필터 체인의 {@code ExceptionTranslationFilter}
     * (→ {@code RestAccessDeniedHandler})까지 전파되지 않고 이 {@code @RestControllerAdvice}가
     * 먼저 본다 — 이 핸들러가 없으면 catch-all({@code handleUnexpected})이 잡아 403이 아니라
     * 500으로 잘못 응답한다. Spring Security 6의 {@code AuthorizationDeniedException}도
     * {@code AccessDeniedException}의 하위 타입이라 함께 잡힌다.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        ErrorCode code = ErrorCode.FORBIDDEN;
        return ResponseEntity.status(code.httpStatus())
                .body(new ErrorResponse(code.name(), code.defaultMessage(), nowOffset(), Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("처리하지 못한 예외", ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(code.name(), code.defaultMessage(), nowOffset(), Map.of()));
    }

    private java.time.OffsetDateTime nowOffset() {
        return serverClock.now().atZone(serverClock.zone()).toOffsetDateTime();
    }
}
