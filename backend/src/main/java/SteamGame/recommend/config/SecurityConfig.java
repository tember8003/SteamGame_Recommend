package SteamGame.recommend.config;

import SteamGame.recommend.infra.web.RateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

// 공개·무상태 REST 백본: 인증 없이 전 요청 허용하되 CORS·보안헤더를 강제함
@Configuration
public class SecurityConfig {

    private final List<String> allowedOriginPatterns;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(@Value("${app.cors.allowed-origins}") String allowedOrigins,
                          RateLimitFilter rateLimitFilter) {
        this.allowedOriginPatterns = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.sameOrigin())
                        .httpStrictTransportSecurity(Customizer.withDefaults())
                        .referrerPolicy(Customizer.withDefaults()))
                .addFilterBefore(rateLimitFilter, AuthorizationFilter.class);
        // CSP 는 swagger-ui 충돌 위험으로 이번 스코프 제외함
        return http.build();
    }

    // RateLimitFilter 는 @Component 라 Boot 가 컨테이너 레벨 필터로도 자동 등록함, addFilterBefore 로 지정한 체인 위치(CORS 필터 뒤)에서만 명시적으로 한 번 돌도록 자동 등록을 끔
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter rateLimitFilter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(rateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Vercel 프리뷰는 배포마다 URL 이 바뀌므로 패턴으로 허용한다, allowCredentials=false 라 와일드카드가 안전하다
        config.setAllowedOriginPatterns(allowedOriginPatterns);
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
