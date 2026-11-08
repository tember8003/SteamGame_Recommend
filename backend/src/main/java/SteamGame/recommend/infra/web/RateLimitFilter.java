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
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

// 지정한 엔드포인트만 IP당 토큰버킷으로 제한, 초과 시 429 + Retry-After 반환함
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // 요청 경로 → app.ratelimit.endpoints 키 매핑
    private static final Map<String, String> PATH_TO_KEY = Map.ofEntries(
            Map.entry("/api/recommend/input", "input"),
            Map.entry("/api/recommend/profile", "profile"),
            Map.entry("/api/recommend/recent-play", "recent-play"),
            Map.entry("/api/recommend/feedback", "feedback"),
            Map.entry("/api/recommend/match", "match"),
            Map.entry("/api/metrics/dashboard", "metrics"),
            Map.entry("/api/metrics/status", "metrics"),
            Map.entry("/api/taste/result", "taste-result"),
            Map.entry("/api/taste/recommend", "taste-recommend"),
            Map.entry("/api/events", "events"),
            Map.entry("/api/library/analyze", "library-analyze"),
            Map.entry("/api/library/share", "library-share")
    );

    // 이 경로들은 POST가 아닌 요청은 제한하지 않는다
    private static final Set<String> POST_ONLY_PATHS =
            Set.of("/api/library/analyze", "/api/library/share");

    // 라우팅과 같은 규칙으로 디코딩한 경로로 비교한다, 원문 URI만 보면 %61 같은 인코딩으로 제한을 피할 수 있음
    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

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
        String path = PATH_HELPER.getLookupPathForRequest(request);
        if (POST_ONLY_PATHS.contains(path) && !"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !PATH_TO_KEY.containsKey(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String endpointKey = PATH_TO_KEY.get(PATH_HELPER.getLookupPathForRequest(request));
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
