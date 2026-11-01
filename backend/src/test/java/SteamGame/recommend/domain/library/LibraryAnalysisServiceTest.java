package SteamGame.recommend.domain.library;

import SteamGame.recommend.config.JacksonConfig;
import SteamGame.recommend.domain.library.content.LibraryContent;
import SteamGame.recommend.domain.library.dto.LibraryViewMapper;
import SteamGame.recommend.domain.library.dto.SharedAnalysisResponse;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import SteamGame.recommend.domain.taste.content.Lang;
import com.fasterxml.jackson.databind.node.ObjectNode;
import SteamGame.recommend.domain.library.content.LibraryContentLoader;
import SteamGame.recommend.domain.library.share.LibraryShareService;
import SteamGame.recommend.domain.library.steam.OwnedGame;
import SteamGame.recommend.domain.library.steam.SteamIdResolver;
import SteamGame.recommend.domain.library.steam.SteamLibraryClient;
import SteamGame.recommend.domain.library.steam.SteamLibraryResult;
import SteamGame.recommend.domain.library.steam.SteamProfile;
import SteamGame.recommend.domain.tag.repository.TagRepository;
import SteamGame.recommend.domain.taste.service.TagRarityCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LibraryAnalysisServiceTest {

    static final String STEAM_ID = "76561198012345678";
    static final String PERSONA = "SecretNick";
    static final String AVATAR = "https://avatars.example/secret-avatar.jpg";

    SteamIdResolver resolver = mock(SteamIdResolver.class);
    SteamLibraryClient client = mock(SteamLibraryClient.class);
    TagRepository tagRepository = mock(TagRepository.class);
    TagRarityCache rarityCache = mock(TagRarityCache.class);
    LibraryContentLoader loader = mock(LibraryContentLoader.class);
    LibraryShareService shareService = mock(LibraryShareService.class);
    // 운영과 같은 설정(JacksonConfig)으로 저장하고 읽는다
    ObjectMapper objectMapper = new JacksonConfig().objectMapper();
    AtomicLong nanos = new AtomicLong();
    LibraryAnalysisService service;

    @BeforeEach
    void setUp() {
        when(loader.content()).thenReturn(LibraryFixtures.validContent());
        when(resolver.resolve(anyString())).thenReturn(STEAM_ID);
        when(rarityCache.isLoaded()).thenReturn(true);
        when(rarityCache.rarity(anyString())).thenReturn(0.5);
        when(tagRepository.findGameTagMapping(anyList())).thenReturn(List.of(
                Map.of("appId", 1L, "tagName", "STRATEGY_A"),
                Map.of("appId", 2L, "tagName", "STRATEGY_B")));
        service = new LibraryAnalysisService(resolver, client, tagRepository, rarityCache, loader, shareService,
                objectMapper, nanos::get);
    }

    private SteamLibraryResult.Found found() {
        return new SteamLibraryResult.Found(new SteamProfile(STEAM_ID, PERSONA, AVATAR), List.of(
                new OwnedGame(1, "Alpha", 600, 60, null),
                new OwnedGame(2, "Beta", 120, 0, null),
                new OwnedGame(3, "Gamma", 0, 0, null)));
    }

    @Test
    void analyze_found_returnsAnalysisAndProfile() {
        when(client.fetch(STEAM_ID)).thenReturn(found());

        LibraryAnalysisService.Analyzed result = service.analyze("whatever");

        assertThat(result.analysisId()).isNotBlank();
        assertThat(result.profile().personaName()).isEqualTo(PERSONA);
        assertThat(result.analysis().stats().ownedCount()).isEqualTo(3);
        assertThat(result.analysis().stats().matchedCount()).isEqualTo(2);
    }

    @Test
    void analyze_private_403() {
        when(client.fetch(STEAM_ID)).thenReturn(new SteamLibraryResult.Private());
        assertStatus(() -> service.analyze("x"), HttpStatus.FORBIDDEN, "LIBRARY_PRIVATE");
    }

    @Test
    void analyze_notFound_404() {
        when(client.fetch(STEAM_ID)).thenReturn(new SteamLibraryResult.NotFound());
        assertStatus(() -> service.analyze("x"), HttpStatus.NOT_FOUND, "STEAM_PROFILE_NOT_FOUND");
    }

    @Test
    void analyze_empty_422() {
        when(client.fetch(STEAM_ID)).thenReturn(new SteamLibraryResult.Empty());
        assertStatus(() -> service.analyze("x"), HttpStatus.UNPROCESSABLE_ENTITY, "LIBRARY_EMPTY");
    }

    @Test
    void analyze_unavailable_503() {
        when(client.fetch(STEAM_ID)).thenReturn(new SteamLibraryResult.Unavailable("timeout"));
        assertStatus(() -> service.analyze("x"), HttpStatus.SERVICE_UNAVAILABLE, "STEAM_UNAVAILABLE");
    }

    @Test
    void analyze_resolverError_isNotWrapped() {
        ResponseStatusException invalid = new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_STEAM_ID");
        when(resolver.resolve(anyString())).thenThrow(invalid);
        assertThatThrownBy(() -> service.analyze("bad")).isSameAs(invalid);
        verify(client, never()).fetch(any());
    }

    @Test
    void share_snapshotHasNoIdentity() {
        when(client.fetch(STEAM_ID)).thenReturn(found());
        when(shareService.save(anyString())).thenReturn("SHARE22");
        LibraryAnalysisService.Analyzed result = service.analyze("x");

        String shareId = service.share(result.analysisId());

        assertThat(shareId).isEqualTo("SHARE22");
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(shareService).save(payload.capture());
        assertThat(payload.getValue())
                .doesNotContain(STEAM_ID)
                .doesNotContain(PERSONA)
                .doesNotContain(AVATAR)
                .doesNotContain("personaName")
                .doesNotContain("avatarUrl")
                .doesNotContain("steamId");
    }

    @Test
    void share_unknownAnalysisId_404() {
        assertStatus(() -> service.share("nope"), HttpStatus.NOT_FOUND, "ANALYSIS_EXPIRED");
        verify(shareService, never()).save(anyString());
    }

    @Test
    void analysisCache_expiresAfter30Minutes() {
        when(client.fetch(STEAM_ID)).thenReturn(found());
        when(shareService.save(anyString())).thenReturn("S");
        String id = service.analyze("x").analysisId();

        nanos.set(Duration.ofMinutes(29).toNanos());
        assertThat(service.share(id)).isEqualTo("S");

        nanos.set(Duration.ofMinutes(31).toNanos());
        assertStatus(() -> service.share(id), HttpStatus.NOT_FOUND, "ANALYSIS_EXPIRED");
    }

    private String savedPayload() {
        when(client.fetch(STEAM_ID)).thenReturn(found());
        when(shareService.save(anyString())).thenReturn("S");
        service.share(service.analyze("x").analysisId());
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(shareService).save(payload.capture());
        return payload.getValue();
    }

    @Test
    void renderShared_roundTripsVersionedSnapshot() throws Exception {
        String payload = savedPayload();
        assertThat(objectMapper.readTree(payload).get("v").asInt()).isEqualTo(1);
        when(shareService.find("S")).thenReturn(Optional.of(payload));

        SharedAnalysisResponse r = service.renderShared("S", Lang.KO);

        assertThat(r.stats().ownedCount()).isEqualTo(3);
        assertThat(r.gamerStats()).hasSize(5);
    }

    @Test
    void renderShared_payloadWithoutVersionIsV1() throws Exception {
        String wrapped = savedPayload();
        String legacy = objectMapper.readTree(wrapped).get("analysis").toString();
        when(shareService.find("S")).thenReturn(Optional.of(legacy));

        assertThat(service.renderShared("S", Lang.EN).stats().ownedCount()).isEqualTo(3);
    }

    @Test
    void renderShared_outOfRangePhraseIndexIsClamped() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(savedPayload());
        ((ObjectNode) root.get("analysis").get("ledger")).put("phraseIndex", 99);
        when(shareService.find("S")).thenReturn(Optional.of(root.toString()));

        SharedAnalysisResponse r = service.renderShared("S", Lang.KO);

        assertThat(r.ledger().text()).isNotBlank();
    }

    @Test
    void renderShared_unknownStatCode_404() {
        String payload = savedPayload().replace("\"EXPLORATION\"", "\"NO_SUCH_STAT\"");
        when(shareService.find("S")).thenReturn(Optional.of(payload));
        assertStatus(() -> service.renderShared("S", Lang.KO), HttpStatus.NOT_FOUND, "SHARE_NOT_FOUND");
    }

    @Test
    void renderShared_garbage_404() {
        when(shareService.find("S")).thenReturn(Optional.of("not json"));
        assertStatus(() -> service.renderShared("S", Lang.KO), HttpStatus.NOT_FOUND, "SHARE_NOT_FOUND");
    }

    @Test
    void renderShared_unknown_404() {
        when(shareService.find("zzz")).thenReturn(Optional.empty());
        assertStatus(() -> service.renderShared("zzz", Lang.KO), HttpStatus.NOT_FOUND, "SHARE_NOT_FOUND");
    }

    // ---- 공유 중복 방지

    @Test
    void share_sameAnalysisReturnsSameShareIdWithOneRow() {
        when(client.fetch(STEAM_ID)).thenReturn(found());
        when(shareService.save(anyString())).thenReturn("S1", "S2");
        String id = service.analyze("x").analysisId();

        String first = service.share(id);
        String second = service.share(id);

        assertThat(first).isEqualTo("S1");
        assertThat(second).isEqualTo("S1");
        verify(shareService, times(1)).save(anyString());
    }

    @Test
    void share_differentAnalysesGetOwnShareIds() {
        when(client.fetch(STEAM_ID)).thenReturn(found());
        when(shareService.save(anyString())).thenReturn("S1", "S2");

        String a = service.share(service.analyze("x").analysisId());
        String b = service.share(service.analyze("x").analysisId());

        assertThat(a).isEqualTo("S1");
        assertThat(b).isEqualTo("S2");
    }

    @Test
    void share_failedSaveIsNotRemembered() {
        when(client.fetch(STEAM_ID)).thenReturn(found());
        when(shareService.save(anyString())).thenThrow(new IllegalStateException("db")).thenReturn("S2");
        String id = service.analyze("x").analysisId();

        assertThatThrownBy(() -> service.share(id)).isInstanceOf(IllegalStateException.class);

        assertThat(service.share(id)).isEqualTo("S2");
    }

    // ---- 저장 형식

    private static String resource(String path) throws Exception {
        try (var in = LibraryAnalysisServiceTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ListAppender<ILoggingEvent> captureLogs() {
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(LibraryAnalysisService.class)).addAppender(logs);
        return logs;
    }

    private void releaseLogs(ListAppender<ILoggingEvent> logs) {
        ((Logger) LoggerFactory.getLogger(LibraryAnalysisService.class)).detachAppender(logs);
    }

    @Test
    void goldenSnapshotV1StillDeserializesAndRenders() throws Exception {
        LibraryContent real = new LibraryContentLoader(new ObjectMapper()).content();
        when(loader.content()).thenReturn(real);
        when(shareService.find("G")).thenReturn(Optional.of(resource("/library/share/snapshot-v1.json")));

        SharedAnalysisResponse r = service.renderShared("G", Lang.EN);

        assertThat(r.limitedData()).isFalse();
        assertThat(r.stats().ownedCount()).isEqualTo(42);
        assertThat(r.stats().totalHours()).isEqualTo(310.5);
        assertThat(r.stats().mostPlayed().name()).isEqualTo("Golden Game");
        assertThat(r.stats().topTags()).extracting(t -> t.tag()).containsExactly("Strategy", "Turn-Based");
        assertThat(r.ledger().code()).isEqualTo("PRACTICAL");
        assertThat(r.ledger().text()).isEqualTo(real.phrases().get("PRACTICAL").get(1).text().en());
        assertThat(r.ledger().params()).containsEntry("ownedCount", 42);
        assertThat(r.verdict().crime().text()).isEqualTo(real.phrases().get("GENRE_LOYALIST").get(1).text().en());
        assertThat(r.verdict().crime().params())
                .containsEntry("topGroupRatioPct", 72)
                .containsEntry("topGroup", real.groupLabels().get("STRATEGY").en());
        assertThat(r.verdict().sentence().text())
                .isEqualTo(real.phrases().get("SENTENCE_GENRE_LOYALIST").get(2).text().en());
        assertThat(r.gamerStats()).extracting(s -> s.code() + ":" + s.value())
                .containsExactly("EXPLORATION:20", "STRATEGY:100", "COLLECTION:38", "DIVERSITY:null", "COOP:4");
        assertThat(r.diagnosis().favoriteTag()).isEqualTo("Strategy");
        assertThat(r.diagnosis().hiddenTaste()).isNull();
    }

    @Test
    void renderShared_unsupportedVersion_404WithWarn() throws Exception {
        ListAppender<ILoggingEvent> logs = captureLogs();
        try {
            for (String v : new String[] {"2", "0", "\"1\"", "null"}) {
                String payload = resource("/library/share/snapshot-v1.json").replace("\"v\": 1", "\"v\": " + v);
                when(shareService.find("S")).thenReturn(Optional.of(payload));

                assertStatus(() -> service.renderShared("S", Lang.KO), HttpStatus.NOT_FOUND, "SHARE_NOT_FOUND");
            }
            assertThat(logs.list).hasSize(4).allSatisfy(e -> assertThat(e.getLevel()).isEqualTo(Level.WARN));
        } finally {
            releaseLogs(logs);
        }
    }

    @Test
    void renderShared_missingOrNullRequiredFields_404WithError() throws Exception {
        String[][] breaks = {
                {"/analysis/limitedData", "remove"},
                {"/analysis/stats", "remove"},
                {"/analysis/stats/ownedCount", "remove"},
                {"/analysis/stats/totalHours", "null"},
                {"/analysis/stats/topTags", "remove"},
                {"/analysis/ledger/params", "remove"},
                {"/analysis/verdict/crime", "null"},
                {"/analysis/gamerStats", "remove"},
                {"/analysis/diagnosis/concentratedGames", "remove"},
                {"/analysis/gamerStats/0/code", "remove"},
                {"/analysis/gamerStats/0/value", "remove"},
        };
        ListAppender<ILoggingEvent> logs = captureLogs();
        try {
            for (String[] b : breaks) {
                ObjectNode root = (ObjectNode) objectMapper.readTree(resource("/library/share/snapshot-v1.json"));
                int cut = b[0].lastIndexOf('/');
                ObjectNode parent = (ObjectNode) (cut == 0 ? root : root.at(b[0].substring(0, cut)));
                String field = b[0].substring(cut + 1);
                if (b[1].equals("remove")) {
                    parent.remove(field);
                } else {
                    parent.putNull(field);
                }
                logs.list.clear();
                when(shareService.find("S")).thenReturn(Optional.of(root.toString()));

                assertThatThrownBy(() -> service.renderShared("S", Lang.KO)).as(b[0] + " " + b[1])
                        .isInstanceOfSatisfying(ResponseStatusException.class,
                                e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
                assertThat(logs.list).as(b[0]).anySatisfy(e -> {
                    assertThat(e.getLevel()).isEqualTo(Level.ERROR);
                    assertThat(e.getFormattedMessage()).isEqualTo("library snapshot unreadable shareId=S");
                });
            }
        } finally {
            releaseLogs(logs);
        }
    }

    @Test
    void renderShared_unknownExtraFieldsAreIgnored() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(resource("/library/share/snapshot-v1.json"));
        root.putObject("futureRoot").put("x", 1);
        ((ObjectNode) root.at("/analysis")).put("futureField", "y");
        ((ObjectNode) root.at("/analysis/stats")).put("futureStat", 3);
        ((ObjectNode) root.at("/analysis/gamerStats/0")).put("futureFlag", true);
        when(shareService.find("S")).thenReturn(Optional.of(root.toString()));

        assertThat(service.renderShared("S", Lang.KO).stats().ownedCount()).isEqualTo(42);
    }

    @Test
    void renderShared_contentDefectPropagatesInsteadOf404() throws Exception {
        LibraryContent withoutLedger = LibraryFixtures.withPhrases(LibraryFixtures.validContent(), "PRACTICAL", null);
        when(loader.content()).thenReturn(withoutLedger);
        when(shareService.find("G")).thenReturn(Optional.of(resource("/library/share/snapshot-v1.json")));

        assertThatThrownBy(() -> service.renderShared("G", Lang.KO))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(ResponseStatusException.class);
    }

    @Test
    void renderShared_databaseFailurePropagatesInsteadOf404() {
        when(shareService.find("S")).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.renderShared("S", Lang.KO))
                .isInstanceOf(IllegalStateException.class).hasMessage("db down");
    }

    // 운영과 같은 ObjectMapper 설정으로 저장하고 읽어도 처음 분석과 같은 응답이 나와야 한다
    private void assertRoundTripEquals(List<OwnedGame> games) {
        when(client.fetch(STEAM_ID)).thenReturn(
                new SteamLibraryResult.Found(new SteamProfile(STEAM_ID, PERSONA, AVATAR), games));
        when(shareService.save(anyString())).thenReturn("S");
        LibraryAnalysisService.Analyzed analyzed = service.analyze("x");
        service.share(analyzed.analysisId());
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(shareService).save(payload.capture());
        when(shareService.find("S")).thenReturn(Optional.of(payload.getValue()));

        for (Lang lang : Lang.values()) {
            assertThat(service.renderShared("S", lang)).isEqualTo(
                    LibraryViewMapper.toSharedResponse(analyzed.analysis(), LibraryFixtures.validContent(), lang));
        }
    }

    @Test
    void roundTrip_normalAccount() {
        assertRoundTripEquals(found().games());
    }

    @Test
    void roundTrip_zeroPlaytimeAccount() {
        List<OwnedGame> zero = List.of(new OwnedGame(1, "Alpha", 0, 0, null), new OwnedGame(2, "Beta", 0, 0, null),
                new OwnedGame(3, "Gamma", 0, 0, null));
        assertRoundTripEquals(zero);
        // 이 계정은 mostPlayed, 일부 지표, hiddenTaste가 모두 비어 있는 경우다
        assertThat(service.renderShared("S", Lang.KO).stats().mostPlayed()).isNull();
    }

    @Test
    void analyze_vanityInput_profileCarriesResolvedSteamId64() {
        when(resolver.resolve("https://steamcommunity.com/id/gaben")).thenReturn(STEAM_ID);
        when(client.fetch(STEAM_ID)).thenReturn(new SteamLibraryResult.Found(
                new SteamProfile("other", PERSONA, AVATAR), found().games()));

        LibraryAnalysisService.Analyzed result = service.analyze("https://steamcommunity.com/id/gaben");

        assertThat(result.profile().steamId64()).isEqualTo(STEAM_ID);
    }

    @Test
    void analyze_steamId64NeverReachesCacheOrSnapshot() {
        when(client.fetch(STEAM_ID)).thenReturn(found());
        when(shareService.save(anyString())).thenReturn("S");
        ListAppender<ILoggingEvent> logs = captureLogs();
        try {
            service.share(service.analyze("x").analysisId());
            ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
            verify(shareService).save(payload.capture());
            assertThat(payload.getValue()).doesNotContain(STEAM_ID).doesNotContainIgnoringCase("steamId64");
            for (ILoggingEvent e : logs.list) {
                assertThat(e.getFormattedMessage()).doesNotContain(STEAM_ID);
            }
        } finally {
            releaseLogs(logs);
        }
    }

    @Test
    void rarityNotLoadedWarnIsLimitedToOncePerMinute() {
        when(client.fetch(STEAM_ID)).thenReturn(found());
        when(rarityCache.isLoaded()).thenReturn(false);
        ListAppender<ILoggingEvent> logs = captureLogs();
        try {
            service.analyze("x");
            service.analyze("x");
            assertThat(logs.list.stream().filter(e -> e.getLevel() == Level.WARN)).hasSize(1);

            nanos.set(Duration.ofSeconds(61).toNanos());
            service.analyze("x");
            assertThat(logs.list.stream().filter(e -> e.getLevel() == Level.WARN)).hasSize(2);
        } finally {
            releaseLogs(logs);
        }
    }

    // ---- 태그 조회

    @Test
    void tagLookup_splitsIntoChunksOf1000AndCountsEveryChunk() {
        List<OwnedGame> games = new ArrayList<>();
        for (long i = 1; i <= 2500; i++) {
            games.add(new OwnedGame(i, "G" + i, 10, 0, null));
        }
        games.add(new OwnedGame(1, "G1 again", 10, 0, null));
        when(client.fetch(STEAM_ID)).thenReturn(
                new SteamLibraryResult.Found(new SteamProfile(STEAM_ID, PERSONA, AVATAR), games));
        // 조각마다 다른 태그를 돌려준다, 첫 조각만 쓰거나 덮어쓰면 matchedCount가 줄어든다
        when(tagRepository.findGameTagMapping(anyList())).thenAnswer(inv -> {
            List<Long> chunk = inv.getArgument(0);
            String tag = "CHUNK_TAG_" + (chunk.get(0) - 1) / 1000;
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Long appid : chunk) {
                rows.add(Map.of("appId", appid, "tagName", tag));
            }
            return rows;
        });

        LibraryAnalysisService.Analyzed result = service.analyze("x");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> chunks = ArgumentCaptor.forClass(List.class);
        verify(tagRepository, times(3)).findGameTagMapping(chunks.capture());
        assertThat(chunks.getAllValues()).extracting(List::size).containsExactly(1000, 1000, 500);
        assertThat(chunks.getAllValues().stream().flatMap(List::stream).distinct().count()).isEqualTo(2500);
        assertThat(result.analysis().stats().matchedCount()).isEqualTo(2501);
    }

    // ---- 희소도를 못 읽었을 때

    private List<OwnedGame> sevenTagGames() {
        List<OwnedGame> games = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (long i = 1; i <= 7; i++) {
            games.add(new OwnedGame(i, "G" + i, (int) (1000 * (8 - i)), 0, null));
            rows.add(Map.of("appId", i, "tagName", "T" + i));
        }
        when(client.fetch(STEAM_ID)).thenReturn(
                new SteamLibraryResult.Found(new SteamProfile(STEAM_ID, PERSONA, AVATAR), games));
        when(tagRepository.findGameTagMapping(anyList())).thenReturn(rows);
        return games;
    }

    @Test
    void hiddenTaste_comesFromRarityWhenLoaded() {
        sevenTagGames();
        when(rarityCache.isLoaded()).thenReturn(true);

        assertThat(service.analyze("x").analysis().diagnosis().hiddenTaste()).isEqualTo("T6");
    }

    @Test
    void hiddenTaste_isNullWhenRarityNotLoaded() {
        sevenTagGames();
        when(rarityCache.isLoaded()).thenReturn(false);
        ListAppender<ILoggingEvent> logs = captureLogs();
        try {
            assertThat(service.analyze("x").analysis().diagnosis().hiddenTaste()).isNull();

            verify(rarityCache, never()).rarity(anyString());
            assertThat(logs.list).anySatisfy(e -> {
                assertThat(e.getLevel()).isEqualTo(Level.WARN);
                assertThat(e.getFormattedMessage()).contains("rarity");
            });
        } finally {
            releaseLogs(logs);
        }
    }

    private static void assertStatus(Runnable call, HttpStatus status, String reason) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(status.value());
                    assertThat(e.getReason()).isEqualTo(reason);
                });
    }
}
