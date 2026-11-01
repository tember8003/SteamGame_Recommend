package SteamGame.recommend.domain.library.share;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LibraryShareServiceTest {

    LibraryShareRepository repository = mock(LibraryShareRepository.class);
    LibraryShareService service;

    @BeforeEach
    void setUp() {
        service = new LibraryShareService(repository, 90);
    }

    @Test
    void saveReturns22CharUrlSafeIdAndPassesPayload() {
        String id = service.save("{\"k\":1}");
        assertThat(id).hasSize(22).matches("[A-Za-z0-9_-]{22}");
        verify(repository).insert(eq(id), eq("{\"k\":1}"), any(Instant.class));
    }

    @Test
    void saveSetsExpiryToConfiguredDays() {
        Instant before = Instant.now();
        service.save("{}");
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(repository).insert(anyString(), anyString(), captor.capture());
        Duration d = Duration.between(before, captor.getValue());
        assertThat(d).isBetween(Duration.ofDays(90).minusSeconds(5), Duration.ofDays(90).plusSeconds(5));
    }

    @Test
    void idsDoNotRepeat() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 200; i++) ids.add(service.save("{}"));
        assertThat(ids).hasSize(200);
    }

    @Test
    void findDelegates() {
        when(repository.findActive("abc")).thenReturn(Optional.of("{}"));
        assertThat(service.find("abc")).contains("{}");
        assertThat(service.find("zzz")).isEmpty();
    }

    @Test
    void deleteExpiredReturnsCount() {
        when(repository.deleteExpired()).thenReturn(3);
        assertThat(service.deleteExpired()).isEqualTo(3);
    }
}
