package kr.savepick.account.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 14-project-structure.md §9.4 — 액세스 토큰 검증 → 주체(sub, role, sid) 확보.
 * 토큰이 없거나 무효해도 예외를 던지지 않는다 — SecurityContext를 비워두면
 * 인증이 필요한 엔드포인트에서 AuthenticationEntryPoint가 401을 내려준다 (12번 §3.2).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtAccessTokenIssuer tokenIssuer;

    public JwtAuthenticationFilter(JwtAccessTokenIssuer tokenIssuer) {
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            JwtAccessTokenIssuer.ParsedAccessToken parsed = tokenIssuer.parse(token);
            if (parsed.valid()) {
                AuthenticatedPrincipal principal =
                        new AuthenticatedPrincipal(parsed.memberId(), parsed.role(), parsed.sessionId());
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + parsed.role().name()));
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
