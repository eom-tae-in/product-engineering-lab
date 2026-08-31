package kr.savepick.common.error;

import java.util.Map;

/**
 * domain·application 계층이 업무 규칙 위반을 알릴 때 던진다 (14-project-structure.md §9.3).
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), Map.of());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    public BusinessException(ErrorCode errorCode, String message, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> details() {
        return details;
    }
}
