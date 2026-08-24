package SteamGame.recommend.controller;

import SteamGame.recommend.domain.feedback.entity.FeedbackContext;
import SteamGame.recommend.domain.feedback.service.FeedbackSuppressionService;
import SteamGame.recommend.domain.game.dto.GameNameRequestDTO;
import SteamGame.recommend.domain.game.dto.GameRequestDTO;
import SteamGame.recommend.domain.game.dto.SteamIdRequestDTO;
import SteamGame.recommend.domain.game.dto.TextRequestDTO;
import SteamGame.recommend.domain.impression.service.ImpressionService;
import SteamGame.recommend.domain.recommendation.dto.MatchRequestDTO;
import SteamGame.recommend.domain.recommendation.dto.MatchResultDTO;
import SteamGame.recommend.domain.recommendation.dto.RecommendationResultDTO;
import SteamGame.recommend.domain.recommendation.dto.SteamAppDTO;
import SteamGame.recommend.domain.recommendation.service.MatchService;
import SteamGame.recommend.domain.recommendation.service.RecommendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendControllerImpressionTest {

    RecommendService recommendService = mock(RecommendService.class);
    MatchService matchService = mock(MatchService.class);
    FeedbackSuppressionService suppressionService = mock(FeedbackSuppressionService.class);
    ImpressionService impressionService = mock(ImpressionService.class);

    RecommendController controller = new RecommendController(
            recommendService, matchService, suppressionService, impressionService);

    @BeforeEach
    void stubSuppression() {
        // 억제는 이 테스트 관심사가 아님, 제외 집합을 그대로 통과시켜 플로우가 서비스까지 닿게 함
        when(suppressionService.resolveExcludes(any(), any())).thenReturn(Set.of());
    }

    @Test
    void matchLogsImpressionWithMatchContext() {
        when(matchService.recommend(any())).thenReturn(matchResult(10L, 20L));
        MatchRequestDTO req = new MatchRequestDTO();
        req.setClientId("cid");

        controller.match(req);

        verify(impressionService).record("cid", null, FeedbackContext.MATCH, List.of(10L, 20L));
    }

    @Test
    void inputLogsImpressionWithInputContext() {
        when(recommendService.selectInfo(any(), anyInt(), any(), any(), any())).thenReturn(recResult(11L, 22L));
        TextRequestDTO dto = new TextRequestDTO();
        dto.setClientId("cid");

        controller.inputRandomGame(dto);

        verify(impressionService).record("cid", null, FeedbackContext.INPUT, List.of(11L, 22L));
    }

    @Test
    void randomLogsImpressionWithRandomContext() {
        when(recommendService.findGame(any(), anyInt(), any(), any(), any(), any(), any())).thenReturn(recResult(1L));
        GameRequestDTO req = new GameRequestDTO();
        req.setClientId("cid");

        controller.randomGame(req);

        verify(impressionService).record("cid", null, FeedbackContext.RANDOM, List.of(1L));
    }

    @Test
    void similarLogsImpressionWithSimilarContext() {
        when(recommendService.recommendBySimilarGame(any(), anyInt(), any(), any(), any())).thenReturn(recResult(2L));
        GameNameRequestDTO dto = new GameNameRequestDTO();
        dto.setClientId("cid");

        controller.randomGameBySimilarGame(dto);

        verify(impressionService).record("cid", null, FeedbackContext.SIMILAR, List.of(2L));
    }

    @Test
    void profileLogsImpressionWithProfileContextAndSteamId() {
        when(recommendService.recommendByProfile(any(), anyInt(), any(), any(), any())).thenReturn(recResult(3L));
        SteamIdRequestDTO dto = new SteamIdRequestDTO();
        dto.setClientId("cid");
        dto.setSteamId("steam1");

        controller.randomGameByProfile(dto);

        verify(impressionService).record("cid", "steam1", FeedbackContext.PROFILE, List.of(3L));
    }

    @Test
    void recentPlayLogsImpressionWithRecentContextAndSteamId() {
        when(recommendService.recommendByRecentPlay(any(), anyInt(), any(), any(), any())).thenReturn(recResult(4L));
        SteamIdRequestDTO dto = new SteamIdRequestDTO();
        dto.setClientId("cid");
        dto.setSteamId("steam1");

        controller.randomGameByRecentPlay(dto);

        verify(impressionService).record("cid", "steam1", FeedbackContext.RECENT, List.of(4L));
    }

    private SteamAppDTO app(long appid) {
        SteamAppDTO a = new SteamAppDTO();
        a.setAppid(appid);
        return a;
    }

    private RecommendationResultDTO recResult(long... appids) {
        RecommendationResultDTO r = new RecommendationResultDTO();
        List<SteamAppDTO> games = new ArrayList<>();
        for (long id : appids) {
            games.add(app(id));
        }
        r.setRecommendedGame(games);
        return r;
    }

    private MatchResultDTO matchResult(long... appids) {
        List<SteamAppDTO> games = new ArrayList<>();
        for (long id : appids) {
            games.add(app(id));
        }
        return new MatchResultDTO(null, games, List.of(), List.of(), false);
    }
}
