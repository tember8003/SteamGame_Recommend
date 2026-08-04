package SteamGame.recommend.infra.api;

import SteamGame.recommend.domain.recommendation.dto.ParsedConstraints;
import SteamGame.recommend.domain.recommendation.dto.SessionLength;
import SteamGame.recommend.infra.llm.ChatCompletionRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GeminiConstraintServiceTest {

    ChatCompletionRouter router;
    GeminiConstraintService service;

    @BeforeEach
    void setUp() {
        router = mock(ChatCompletionRouter.class);
        service = new GeminiConstraintService(new ObjectMapper(), router);
    }

    @Test
    void parsesJsonObjectFromRouterOutput() {
        when(router.complete(any())).thenReturn(Optional.of(
                "{\"players\":3,\"budgetUsd\":20,\"sessionLength\":\"SHORT\",\"needsCoop\":true,\"moodText\":\"왁자지껄 협동\",\"excludeTags\":[\"Horror\"]}"
        ));

        ParsedConstraints pc = service.parseConstraints("친구 3명이랑 20달러 이하 협동, 호러 빼줘");

        assertThat(pc.players()).isEqualTo(3);
        assertThat(pc.budgetUsd()).isEqualTo(20);
        assertThat(pc.sessionLength()).isEqualTo(SessionLength.SHORT);
        assertThat(pc.needsCoop()).isTrue();
        assertThat(pc.moodText()).isEqualTo("왁자지껄 협동");
        assertThat(pc.excludeTags()).containsExactly("Horror");
    }

    @Test
    void emptyRouterResultReturnsNull() {
        when(router.complete(any())).thenReturn(Optional.empty());

        assertThat(service.parseConstraints("아무거나")).isNull();
    }

    @Test
    void returnsNullOnMalformed() {
        when(router.complete(any())).thenReturn(Optional.of("not json"));

        assertThat(service.parseConstraints("아무거나")).isNull();
    }

    @Test
    void invalidSessionLengthStringIsAbsorbedToNull() {
        when(router.complete(any())).thenReturn(Optional.of(
                "{\"players\":null,\"budgetUsd\":null,\"sessionLength\":\"UNKNOWN\",\"needsCoop\":null,\"moodText\":null,\"excludeTags\":[]}"
        ));

        ParsedConstraints pc = service.parseConstraints("아무 텍스트");

        assertThat(pc.players()).isNull();
        assertThat(pc.sessionLength()).isNull();
        assertThat(pc.excludeTags()).isEmpty();
    }

    @Test
    void excludeTagsFiltersOutValuesNotInTagList() {
        when(router.complete(any())).thenReturn(Optional.of(
                "{\"players\":null,\"budgetUsd\":null,\"sessionLength\":null,\"needsCoop\":null,\"moodText\":null,\"excludeTags\":[\"Horror\",\"NotARealTag\"]}"
        ));

        ParsedConstraints pc = service.parseConstraints("호러 말고 이상한태그도 빼줘");

        assertThat(pc.excludeTags()).containsExactly("Horror");
    }

    @Test
    void wrongTypedFieldsAreAbsorbedToNullInsteadOfCoerced() {
        when(router.complete(any())).thenReturn(Optional.of(
                "{\"players\":\"three\",\"budgetUsd\":\"twenty\",\"sessionLength\":null,\"needsCoop\":\"yes\",\"moodText\":null,\"excludeTags\":[]}"
        ));

        ParsedConstraints pc = service.parseConstraints("타입이 이상한 응답");

        assertThat(pc.players()).isNull();
        assertThat(pc.budgetUsd()).isNull();
        assertThat(pc.needsCoop()).isNull();
    }

    @Test
    void blankInput_returnsNullWithoutCallingRouter() {
        assertThat(service.parseConstraints("")).isNull();
        assertThat(service.parseConstraints(null)).isNull();
        verifyNoInteractions(router);
    }
}
