package SteamGame.recommend.infra.api;

import SteamGame.recommend.domain.recommendation.dto.ParsedConstraints;
import SteamGame.recommend.infra.llm.ChatCompletionRouter;
import SteamGame.recommend.infra.llm.ChatRequest;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
                "{\"players\":3,\"budgetUsd\":20,\"needsCoop\":true,\"moodText\":\"왁자지껄 협동\",\"excludeTags\":[\"Horror\"]}"
        ));

        ParsedConstraints pc = service.parseConstraints("친구 3명이랑 20달러 이하 협동, 호러 빼줘");

        assertThat(pc.players()).isEqualTo(3);
        assertThat(pc.budgetUsd()).isEqualTo(20);
        assertThat(pc.needsCoop()).isTrue();
        assertThat(pc.moodText()).isEqualTo("왁자지껄 협동");
        assertThat(pc.excludeTags()).containsExactly("Horror");

        // 라우터로 전송된 스키마가 스왑, 오타 없이 strict 요구사항을 지키는지 구조로 검증함, 상수 자체는 참조하지 않음
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(router).complete(captor.capture());
        ChatRequest sent = captor.getValue();

        assertThat(sent.schemaName()).isEqualTo("constraints");
        assertThat(sent.jsonSchema().get("additionalProperties")).isEqualTo(false);

        List<String> expectedFields = List.of(
                "players", "budgetUsd", "needsCoop", "moodText", "excludeTags");
        @SuppressWarnings("unchecked")
        List<Object> required = (List<Object>) sent.jsonSchema().get("required");
        assertThat(required).containsExactlyElementsOf(expectedFields);

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) sent.jsonSchema().get("properties");
        assertThat(properties.keySet()).containsExactlyInAnyOrderElementsOf(expectedFields);
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
    void unknownExtraFieldFromModelIsIgnored() {
        // 스키마에서 뺀 필드를 모델이 그래도 내보내도 파싱이 깨지지 않아야 한다
        when(router.complete(any())).thenReturn(Optional.of(
                "{\"players\":2,\"budgetUsd\":null,\"sessionLength\":\"SHORT\",\"needsCoop\":null,\"moodText\":null,\"excludeTags\":[]}"
        ));

        ParsedConstraints pc = service.parseConstraints("아무 텍스트");

        assertThat(pc.players()).isEqualTo(2);
        assertThat(pc.excludeTags()).isEmpty();
    }

    @Test
    void schemaAndPromptNoLongerMentionSessionLength() {
        when(router.complete(any())).thenReturn(Optional.empty());

        service.parseConstraints("짧게 즐길 게임");

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(router).complete(captor.capture());
        ChatRequest sent = captor.getValue();
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) sent.jsonSchema().get("properties");
        assertThat(properties).doesNotContainKey("sessionLength");
        assertThat(sent.jsonSchema().get("required").toString()).doesNotContain("sessionLength");
        assertThat(sent.user()).doesNotContain("sessionLength");
    }

    @Test
    void excludeTagsFiltersOutValuesNotInTagList() {
        when(router.complete(any())).thenReturn(Optional.of(
                "{\"players\":null,\"budgetUsd\":null,\"needsCoop\":null,\"moodText\":null,\"excludeTags\":[\"Horror\",\"NotARealTag\"]}"
        ));

        ParsedConstraints pc = service.parseConstraints("호러 말고 이상한태그도 빼줘");

        assertThat(pc.excludeTags()).containsExactly("Horror");
    }

    @Test
    void wrongTypedFieldsAreAbsorbedToNullInsteadOfCoerced() {
        when(router.complete(any())).thenReturn(Optional.of(
                "{\"players\":\"three\",\"budgetUsd\":\"twenty\",\"needsCoop\":\"yes\",\"moodText\":null,\"excludeTags\":[]}"
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
