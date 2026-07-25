package SteamGame.recommend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

// IP당 요청 제한 설정. app.ratelimit.endpoints.<이름>.* 를 바인딩.
// 예) input 을 "1분에 10회"로: capacity=10, refill-tokens=10, period-minutes=1
@Component
@ConfigurationProperties(prefix = "app.ratelimit")
public class RateLimitProperties {

    // 키 = 엔드포인트 이름(input/profile/recent-play), 값 = 그 제한 설정.
    // 요청 경로 → 이 키 변환은 RateLimitFilter.PATH_TO_KEY 참고.
    private Map<String, Limit> endpoints = new HashMap<>();

    public Map<String, Limit> getEndpoints() { return endpoints; }
    public void setEndpoints(Map<String, Limit> endpoints) { this.endpoints = endpoints; }

    public static class Limit {
        private int capacity;          // 순간 최대 허용량 (버킷 크기)
        private int refillTokens;      // 주기마다 다시 채우는 토큰 수
        private int periodMinutes = 1; // 토큰 채우는 주기 (분)

        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public int getRefillTokens() { return refillTokens; }
        public void setRefillTokens(int refillTokens) { this.refillTokens = refillTokens; }
        public int getPeriodMinutes() { return periodMinutes; }
        public void setPeriodMinutes(int periodMinutes) { this.periodMinutes = periodMinutes; }
    }
}
