package kr.savepick.account.infrastructure;

import java.util.UUID;
import kr.savepick.account.domain.Role;

/**
 * JWT 검증 후 SecurityContext에 담기는 인증 주체 (12-auth.md §1.3).
 * 다른 도메인의 컨트롤러도 @AuthenticationPrincipal로 이 타입을 주입받아 sub/role을 확인한다
 * (14-project-structure.md §4.1 — 다른 도메인이 account를 읽는 것은 허용된다).
 */
public record AuthenticatedPrincipal(Long memberId, Role role, UUID sessionId) {
}
