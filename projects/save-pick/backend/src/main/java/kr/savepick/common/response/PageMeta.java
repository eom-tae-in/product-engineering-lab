package kr.savepick.common.response;

/**
 * 11-api-spec.md §0.4 — 목록 응답의 공통 페이지 정보 형태
 * ({@code { "number": 0, "size": 20, "totalElements": 37 } }).
 */
public record PageMeta(int number, int size, long totalElements) {
}
