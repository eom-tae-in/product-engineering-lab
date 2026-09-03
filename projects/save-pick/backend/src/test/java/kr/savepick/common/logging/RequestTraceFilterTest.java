package kr.savepick.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 14-project-structure.md §9.5 — 밖에서 온 요청 식별자를 어디까지 믿을지 검증한다.
 * 이 값은 모든 로그 줄에 그대로 찍히므로 길이와 문자를 제한한다.
 */
class RequestTraceFilterTest {

    @Test
    @DisplayName("헤더가_없으면_새_추적값을_만든다")
    void 헤더가_없으면_새_추적값을_만든다() {
        String traceId = RequestTraceFilter.resolveTraceId(null);

        assertThat(traceId).isNotBlank().hasSize(8);
    }

    @Test
    @DisplayName("앞단이_준_식별자는_그대로_이어_쓴다")
    void 앞단이_준_식별자는_그대로_이어_쓴다() {
        assertThat(RequestTraceFilter.resolveTraceId("edge-7f3a91")).isEqualTo("edge-7f3a91");
    }

    @Test
    @DisplayName("줄바꿈이_섞인_값은_걸러_로그_주입을_막는다")
    void 줄바꿈이_섞인_값은_걸러_로그_주입을_막는다() {
        String traceId = RequestTraceFilter.resolveTraceId("abc\nINFO 위조된 로그 줄");

        assertThat(traceId).doesNotContain("\n").doesNotContain(" ").isEqualTo("abcINFO");
    }

    @Test
    @DisplayName("쓸_수_있는_문자가_없으면_새로_만든다")
    void 쓸_수_있는_문자가_없으면_새로_만든다() {
        String traceId = RequestTraceFilter.resolveTraceId("!!! ???");

        assertThat(traceId).hasSize(8).matches("[0-9a-f]{8}");
    }

    @Test
    @DisplayName("지나치게_긴_값은_잘라_쓴다")
    void 지나치게_긴_값은_잘라_쓴다() {
        String traceId = RequestTraceFilter.resolveTraceId("a".repeat(500));

        assertThat(traceId).hasSize(RequestTraceFilter.MAX_TRACE_ID_LENGTH);
    }
}
