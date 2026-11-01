package SteamGame.recommend.domain.library;

import SteamGame.recommend.domain.library.dto.SharedAnalysisResponse;
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

import java.time.Duration;
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
    ObjectMapper objectMapper = new ObjectMapper();
    AtomicLong nanos = new AtomicLong();
    LibraryAnalysisService service;

    @BeforeEach
    void setUp() {
        when(loader.content()).thenReturn(LibraryFixtures.validContent());
        when(resolver.resolve(anyString())).thenReturn(STEAM_ID);
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

    private static void assertStatus(Runnable call, HttpStatus status, String reason) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(status.value());
                    assertThat(e.getReason()).isEqualTo(reason);
                });
    }
}
