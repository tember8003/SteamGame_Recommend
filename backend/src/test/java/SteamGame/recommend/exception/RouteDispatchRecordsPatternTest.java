package SteamGame.recommend.exception;

import SteamGame.recommend.config.JacksonConfig;
import SteamGame.recommend.config.RateLimitProperties;
import SteamGame.recommend.config.SecurityConfig;
import SteamGame.recommend.controller.LibraryController;
import SteamGame.recommend.domain.event.service.FunnelEventService;
import SteamGame.recommend.domain.library.LibraryAnalysisService;
import SteamGame.recommend.domain.library.content.LibraryContentLoader;
import SteamGame.recommend.domain.taste.content.Lang;
import SteamGame.recommend.infra.web.ClientIpResolver;
import SteamGame.recommend.infra.web.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 MVC 디스패치에서 예외 처리기가 남기는 경로가 실제 주소가 아니라 라우트 패턴인지 확인한다, 경로 변수가 있는 LibraryController를 쓴다
@WebMvcTest(LibraryController.class)
@Import({JacksonConfig.class, SecurityConfig.class, RateLimitFilter.class, RateLimitProperties.class, ClientIpResolver.class,
        LibraryContentLoader.class, RecentErrors.class})
class RouteDispatchRecordsPatternTest {

    @Autowired MockMvc mvc;
    @Autowired RecentErrors recentErrors;
    @MockitoBean LibraryAnalysisService service;
    @MockitoBean FunnelEventService eventService;

    @Test
    void dispatch_기록되는_경로는_실제_주소가_아니라_라우트_패턴이다() throws Exception {
        when(service.renderShared("abcdef0123456789", Lang.KO))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "없음"));

        mvc.perform(get("/api/library/share/abcdef0123456789"))
                .andExpect(status().isNotFound());

        RecentErrors.Entry entry = recentErrors.snapshot().recent().get(0);

        assertThat(entry.route()).isEqualTo("/api/library/share/{shareId}");
        assertThat(entry.route()).doesNotContain("abcdef0123456789");
        assertThat(entry.status()).isEqualTo(404);
        assertThat(entry.errorCode()).isEqualTo("NOT_FOUND");
    }
}
