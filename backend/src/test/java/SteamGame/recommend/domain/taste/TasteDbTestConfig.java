package SteamGame.recommend.domain.taste;

import SteamGame.recommend.config.AsyncConfig;
import SteamGame.recommend.domain.event.service.FunnelEventService;
import SteamGame.recommend.domain.taste.content.TasteContentLoader;
import SteamGame.recommend.domain.taste.repository.TasteCandidateRepository;
import SteamGame.recommend.domain.taste.service.TagRarityCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

// 취향 DB 테스트가 같은 스프링 컨텍스트(같은 커넥션 풀)를 공유하도록 설정을 한곳에 모음
@TestConfiguration
@Import({TagRarityCache.class, TasteCandidateRepository.class, AsyncConfig.class, FunnelEventService.class})
public class TasteDbTestConfig {

    @Bean
    TasteContentLoader tasteContentLoader() {
        return new TasteContentLoader(new ObjectMapper());
    }
}
