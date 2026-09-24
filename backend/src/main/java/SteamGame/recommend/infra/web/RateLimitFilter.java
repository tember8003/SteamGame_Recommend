package SteamGame.recommend.infra.web;

import SteamGame.recommend.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

// 지정한 엔드포인트만 IP당 토큰버킷으로 제한, 초과 시 429 + Retry-After 반환함
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // 요청 경로 → app.ratelimit.endpoints 키 매핑
    private static final Map<String, String> PATH_TO_KEY = Map.of(
            "/api/recommend/input", "input",
            "/api/recommend/profile", "profile",
            "/api/recommend/recent-play", "recent-play",
            "/api/recommend/feedback", "feedback",
            "/api/recommend/match", "match",
            "/api/metrics/overview", "metrics"
    );

    private final RateLimitProperties props;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    public RateLimitFilter(RateLimitProperties props,
                           ClientIpResolver clientIpResolver,
                           ObjectMapper objectMapper) {
        this.props = props;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true; // CORS 프리플라이트는 제외함
        }
        return !PATH_TO_KEY.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String endpointKey = PATH_TO_KEY.get(request.getRequestURI());
        RateLimitProperties.Limit limit = props.getEndpoints().get(endpointKey);
        if (limit == null) { // 설정 누락 시 제한 없이 통과함
            filterChain.doFilter(request, response);
            return;
        }

        String bucketKey = endpointKey + ":" + clientIpResolver.resolve(request);
        Bucket bucket = buckets.get(bucketKey, k -> newBucket(limit));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L;
        writeTooManyRequests(response, retryAfterSeconds);
    }

    private Bucket newBucket(RateLimitProperties.Limit limit) {
        // bucket4j 8.3.0 은 Bandwidth.builder() 가 없어 classic(capacity, Refill.greedy(...)) 로 대체함
        Bandwidth bandwidth = Bandwidth.classic(
                limit.getCapacity(),
                Refill.greedy(limit.getRefillTokens(), Duration.ofMinutes(limit.getPeriodMinutes())));
        return Bucket.builder().addLimit(bandwidth).build();
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        // jakarta.servlet.http.HttpServletResponse 에는 429 상수가 없어 리터럴로 지정함
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "error", "rate_limited",
                "retryAfterSeconds", retryAfterSeconds
        )));
    }
}
