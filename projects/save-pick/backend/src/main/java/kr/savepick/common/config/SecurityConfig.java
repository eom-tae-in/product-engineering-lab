package kr.savepick.common.config;

import java.util.List;
import kr.savepick.account.infrastructure.JwtAccessTokenIssuer;
import kr.savepick.account.infrastructure.JwtAuthenticationFilter;
import kr.savepick.account.infrastructure.RestAccessDeniedHandler;
import kr.savepick.account.infrastructure.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 12-auth.md §3.1 인가 판정 순서, §5 P2·P3.
 * 관리자 기능은 경로(/api/admin/**)로 먼저 막고 role로 다시 막는다 (S3).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAccessTokenIssuer tokenIssuer,
            RestAuthenticationEntryPoint entryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/signup", "/api/auth/login", "/api/auth/token/refresh",
                                "/api/admin/auth/login", "/actuator/health",
                                "/api/store", "/api/products", "/api/products/**",
                                "/api/cart", "/api/cart/**", "/api/system/time")
                        .permitAll()
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(tokenIssuer), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * 12번 §5 P3 — 로컬 개발 기본값. 실제 운영 출처는 배포 시 환경변수로 교체한다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Guest-Token", "Idempotency-Key"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
