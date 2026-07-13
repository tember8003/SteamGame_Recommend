package SteamGame.recommend.infra.crawler;

import SteamGame.recommend.infra.crawler.dto.GameSeed;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class SeedCrawlerServiceTest {

    private final SteamSpyClient client = mock(SteamSpyClient.class);
    private final CatalogUpsertRepository upsert = mock(CatalogUpsertRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private SeedCrawlerService newService(CrawlerProperties props) {
        return new SeedCrawlerService(client, new SteamSpyParser(new com.fasterxml.jackson.databind.ObjectMapper()),
                upsert, jdbc, props);
    }

    private CrawlerProperties props(int maxPages) {
        CrawlerProperties p = new CrawlerProperties();
        p.setPageDelayMs(0);          // 테스트는 지연 없음
        p.setMaxPages(maxPages);
        p.setMinReviews(50);
        return p;
    }

    @Test
    void runSeed_filtersLowReviewGames_andUpsertsQualifying() {
        // page 0: 하나는 리뷰 많음(적재), 하나는 리뷰 적음(제외). page 1: 빈 페이지 → 종료
        when(client.fetchAllPage(0)).thenReturn("""
            {"570":{"appid":570,"name":"Dota 2","developer":"Valve","publisher":"Valve","positive":1200000,"negative":250000,"owners":"100,000,000 .. 200,000,000"},
             "999999":{"appid":999999,"name":"Tiny","developer":"d","publisher":"","positive":10,"negative":5,"owners":"0 .. 20,000"}}
            """);
        when(client.fetchAllPage(1)).thenReturn("{}");

        SeedCrawlerService service = newService(props(100));
        SeedCrawlerService.SeedResult result = service.runSeed();

        ArgumentCaptor<List<GameSeed>> captor = ArgumentCaptor.forClass(List.class);
        verify(upsert, atLeastOnce()).upsertGames(captor.capture());
        List<GameSeed> upserted = captor.getAllValues().stream().flatMap(List::stream).toList();

        assertThat(upserted).extracting(GameSeed::appid).containsExactly(570L);   // 999999 제외
        GameSeed dota = upserted.get(0);
        assertThat(dota.reviewCount()).isEqualTo(1450000);
        assertThat(dota.ownersEstimate()).isEqualTo(100000000L);                  // 하한 파싱
        assertThat(result.cappedByMaxPages()).isFalse();
    }

    @Test
    void runSeed_stopsAtMaxPages_andFlagsCapped() {
        when(client.fetchAllPage(anyInt())).thenReturn("""
            {"570":{"appid":570,"name":"Dota 2","developer":"Valve","publisher":"Valve","positive":1200000,"negative":250000,"owners":"100 .. 200"}}
            """);   // 항상 비어있지 않음 → 상한까지 감

        SeedCrawlerService service = newService(props(2));
        SeedCrawlerService.SeedResult result = service.runSeed();

        assertThat(result.pagesFetched()).isEqualTo(2);
        assertThat(result.cappedByMaxPages()).isTrue();
        verify(client).fetchAllPage(0);
        verify(client).fetchAllPage(1);
        verify(client, never()).fetchAllPage(2);
    }

    @Test
    void runSeed_skipsBlankNameEntries() {
        when(client.fetchAllPage(0)).thenReturn("""
            {"570":{"appid":570,"name":"Dota 2","developer":"Valve","publisher":"Valve","positive":1200000,"negative":250000,"owners":"100 .. 200"},
             "111":{"appid":111,"name":"","developer":"d","publisher":"p","positive":9000,"negative":100,"owners":"0 .. 20000"}}
            """);
        when(client.fetchAllPage(1)).thenReturn("{}");

        SeedCrawlerService service = newService(props(100));
        service.runSeed();

        org.mockito.ArgumentCaptor<java.util.List<SteamGame.recommend.infra.crawler.dto.GameSeed>> captor =
                org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(upsert, atLeastOnce()).upsertGames(captor.capture());
        java.util.List<SteamGame.recommend.infra.crawler.dto.GameSeed> upserted =
                captor.getAllValues().stream().flatMap(java.util.List::stream).toList();
        assertThat(upserted).extracting(SteamGame.recommend.infra.crawler.dto.GameSeed::appid)
                .containsExactly(570L);  // 빈 이름 111 제외
    }

    @Test
    void runSeed_survivesCrawlLogInsertFailure() {
        when(client.fetchAllPage(0)).thenReturn("""
            {"570":{"appid":570,"name":"Dota 2","developer":"Valve","publisher":"Valve","positive":1200000,"negative":250000,"owners":"100 .. 200"}}
            """);
        when(client.fetchAllPage(1)).thenReturn("{}");
        // startCrawlLog 의 INSERT ... RETURNING id 가 실패해도 크롤은 계속돼야 한다
        when(jdbc.queryForObject(anyString(), eq(Long.class), any()))
                .thenThrow(new RuntimeException("db down"));

        SeedCrawlerService service = newService(props(100));
        SeedCrawlerService.SeedResult result = service.runSeed();

        verify(upsert, atLeastOnce()).upsertGames(anyList());
        assertThat(result.gamesUpserted()).isEqualTo(1);
    }

    @Test
    void runSeed_marksCrawlLogFailed_andRethrows_onFetchError() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(42L);
        when(client.fetchAllPage(0)).thenThrow(new RuntimeException("steamspy 500"));

        SeedCrawlerService service = newService(props(100));
        assertThatThrownBy(service::runSeed).isInstanceOf(RuntimeException.class);
        // finishCrawlLog 가 FAILED 노트로 UPDATE 되었는지 (note 는 5번째 vararg)
        verify(jdbc).update(anyString(), any(), anyInt(), anyInt(),
                argThat(o -> String.valueOf(o).startsWith("FAILED")), eq(42L));
    }
}
