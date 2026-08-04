package SteamGame.recommend.domain.recommendation.service;

import SteamGame.recommend.domain.recommendation.dto.MatchRequestDTO;
import SteamGame.recommend.domain.recommendation.dto.ParsedConstraints;
import SteamGame.recommend.domain.recommendation.dto.RecommendationCriteria;
import SteamGame.recommend.domain.recommendation.dto.SessionLength;
import SteamGame.recommend.infra.api.GeminiConstraintService;
import SteamGame.recommend.infra.utils.EncryptUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConstraintResolverTest {

    GeminiConstraintService parser = mock(GeminiConstraintService.class);
    CacheService cache = mock(CacheService.class);
    ObjectMapper objectMapper = new ObjectMapper();
    ConstraintResolver resolver = new ConstraintResolver(parser, cache, objectMapper);

    @BeforeEach
    void setUp() {
        // 기존 테스트들은 캐시 미스를 가정하고 파서 경로를 그대로 탄다
        when(cache.getCachedConstraints(any())).thenReturn(null);
    }

    @Test
    void formWinsOverNaturalLanguage() {
        when(parser.parseConstraints(any())).thenReturn(
                new ParsedConstraints(3, 50, SessionLength.LONG, true, "느긋한", List.of("Gore")));
        MatchRequestDTO req = new MatchRequestDTO();
        req.setRawText("아무 문장");
        req.setPlayers(4);
        req.setBudgetUsd(20);
        req.setExcludeTags(List.of("Horror"));

        RecommendationCriteria c = resolver.resolve(req);

        assertThat(c.getPlayers()).isEqualTo(4);
        assertThat(c.getBudgetCents()).isEqualTo(2000);
        assertThat(c.getSessionLength()).isEqualTo(SessionLength.LONG);
        assertThat(c.getExcludeTags()).contains("Horror", "Gore");
        assertThat(c.isNlParseFailed()).isFalse();
    }

    @Test
    void blankRawTextSkipsParser() {
        MatchRequestDTO req = new MatchRequestDTO();
        req.setPlayers(2);

        resolver.resolve(req);

        verifyNoInteractions(parser);
    }

    @Test
    void parserFailureSetsFlagAndUsesFormOnly() {
        when(parser.parseConstraints(any())).thenReturn(null);
        MatchRequestDTO req = new MatchRequestDTO();
        req.setRawText("문장");
        req.setBudgetUsd(10);

        RecommendationCriteria c = resolver.resolve(req);

        assertThat(c.isNlParseFailed()).isTrue();
        assertThat(c.getBudgetCents()).isEqualTo(1000);
    }

    @Test
    void emptyRequestIsEmptyCriteria() {
        RecommendationCriteria c = resolver.resolve(new MatchRequestDTO());

        assertThat(c.isEmpty()).isTrue();
    }

    @Test
    void moodTextCombinesFormTagsAndParsedMood() {
        when(parser.parseConstraints(any())).thenReturn(
                new ParsedConstraints(null, null, null, null, "느긋한 분위기", null));
        MatchRequestDTO req = new MatchRequestDTO();
        req.setRawText("문장");
        req.setMoodTags(List.of("코옵", "캐주얼"));

        RecommendationCriteria c = resolver.resolve(req);

        assertThat(c.getMoodText()).isEqualTo("코옵 캐주얼 느긋한 분위기");
        assertThat(c.hasMood()).isTrue();
    }

    @Test
    void cachedRawTextSkipsParser() {
        // 캐시가 직렬화된 ParsedConstraints 를 돌려주면 parser 를 부르지 않는다
        when(cache.getCachedConstraints(any())).thenReturn("{\"players\":3,\"budgetUsd\":null,\"sessionLength\":null,\"needsCoop\":true,\"moodText\":null,\"excludeTags\":[]}");
        MatchRequestDTO req = new MatchRequestDTO();
        req.setRawText("친구랑 협동");

        RecommendationCriteria c = resolver.resolve(req);

        assertThat(c.getPlayers()).isEqualTo(3);
        verifyNoInteractions(parser);
    }

    @Test
    void cacheMissCallsParserThenStores() {
        when(cache.getCachedConstraints(any())).thenReturn(null);
        when(parser.parseConstraints(any())).thenReturn(new ParsedConstraints(2, null, null, true, null, List.of()));
        MatchRequestDTO req = new MatchRequestDTO();
        req.setRawText("협동");

        resolver.resolve(req);

        verify(cache).cacheConstraints(eq(EncryptUtils.sha256("협동")), anyString());
    }

    @Test
    void corruptCacheEntrySelfHeals() {
        // 손상된 캐시 값(깨진 JSON)은 미스로 취급되어 parser 를 호출하고 정상 결과로 재저장한다
        when(cache.getCachedConstraints(any())).thenReturn("not-json{{");
        when(parser.parseConstraints(any())).thenReturn(new ParsedConstraints(2, null, null, true, null, List.of()));
        MatchRequestDTO req = new MatchRequestDTO();
        req.setRawText("협동");

        resolver.resolve(req);

        verify(parser).parseConstraints(any());
        verify(cache).cacheConstraints(any(), any());
    }

    @Test
    void parseFailureNotCached() {
        when(cache.getCachedConstraints(any())).thenReturn(null);
        when(parser.parseConstraints(any())).thenReturn(null);
        MatchRequestDTO req = new MatchRequestDTO();
        req.setRawText("협동");

        RecommendationCriteria c = resolver.resolve(req);

        assertThat(c.isNlParseFailed()).isTrue();
        verify(cache, never()).cacheConstraints(any(), any());
    }

    @Test
    void emptyParseNotCached() {
        // 전 필드 빈 파싱 결과는 캐시하지 않는다, 일시적 추출 실패가 TTL 동안 박히는 오염을 막음
        when(cache.getCachedConstraints(any())).thenReturn(null);
        when(parser.parseConstraints(any())).thenReturn(
                new ParsedConstraints(null, null, null, null, null, List.of()));
        MatchRequestDTO req = new MatchRequestDTO();
        req.setRawText("그냥 재밌는 거 추천");

        RecommendationCriteria c = resolver.resolve(req);

        assertThat(c.isNlParseFailed()).isFalse();
        verify(cache, never()).cacheConstraints(any(), any());
    }
}
