package SteamGame.recommend.infra.crawler;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TagCooccurrenceRefresherTest {

    final TagCooccurrenceWriter writer = mock(TagCooccurrenceWriter.class);
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final TagCooccurrenceRefresher refresher = new TagCooccurrenceRefresher(writer, jdbc);

    @Test
    void success_returnsPairCount_andRecordsIt() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(11L);
        when(writer.replaceAll()).thenReturn(64000);

        assertThat(refresher.refresh()).isEqualTo(64000);
        verify(jdbc).update(anyString(), any(), eq(64000), eq(0), eq("pairs=64000"), eq(11L));
    }

    @Test
    void failure_isRecordedAsFailed_andRethrown() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(12L);
        IllegalStateException boom = new IllegalStateException("no tags");
        when(writer.replaceAll()).thenThrow(boom);

        assertThatThrownBy(refresher::refresh).isSameAs(boom);
        verify(jdbc).update(anyString(), any(), eq(0), eq(1), eq("FAILED: no tags"), eq(12L));
    }
}
