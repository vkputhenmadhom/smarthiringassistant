package org.vinod.sha.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.vinod.sha.ai.config.AiProperties;
import org.vinod.sha.ai.entity.AICache;
import org.vinod.sha.ai.exception.LlmUnavailableException;
import org.vinod.sha.ai.exception.RateLimitExceededException;
import org.vinod.sha.ai.repository.AICacheRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class LlmServiceTest {

    private static final AiProperties PROPERTIES = new AiProperties(100, 1024, 4096, 5, 60);

    private final ChatModel chatModel = mock(ChatModel.class);
    private final AICacheRepository cacheRepository = mock(AICacheRepository.class);
    private final RateLimitService rateLimitService = mock(RateLimitService.class);
    private final Map<String, AICache> cacheStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> reply("answer"));
        when(rateLimitService.isAllowed(anyString(), anyString())).thenReturn(true);
        when(cacheRepository.findByCacheKey(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(cacheStore.get(invocation.<String>getArgument(0))));
        when(cacheRepository.save(any(AICache.class))).thenAnswer(invocation -> {
            AICache entry = invocation.getArgument(0);
            cacheStore.put(entry.getCacheKey(), entry);
            return entry;
        });
    }

    @Test
    void returnsTheProviderAnswerWithTheModelThatProducedIt() {
        LlmService.Completion completion = service("openai").complete("u1", null, "hello", null);

        assertEquals("answer", completion.text());
        assertEquals("gpt-5.4-mini", completion.model());
        assertEquals(15, completion.tokensUsed());
        assertFalse(completion.cached());
    }

    @Test
    void identicalRequestIsServedFromCacheWithoutSpendingQuota() {
        LlmService service = service("openai");
        service.complete("u1", null, "hello", null);

        LlmService.Completion second = service.complete("u1", null, "hello", null);

        assertTrue(second.cached());
        verify(chatModel, times(1)).call(any(Prompt.class));
        verify(rateLimitService, times(1)).isAllowed(anyString(), anyString());
    }

    @Test
    void promptsWithCollidingHashCodesDoNotShareACacheEntry() {
        // "Aa" and "BB" have the same String.hashCode(); the old 32-bit cache key confused them
        assertEquals("Aa".hashCode(), "BB".hashCode());
        LlmService service = service("openai");
        service.complete("u1", null, "Aa", null);

        assertFalse(service.complete("u1", null, "BB", null).cached());
        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    @Test
    void rejectsOverlongPromptsBeforeCallingTheProvider() {
        assertThrows(IllegalArgumentException.class, () -> service("openai").complete("u1", null, "x".repeat(101), null));
        verifyNoInteractions(chatModel);
    }

    @Test
    void refusesWhenTheCallerIsOverQuota() {
        when(rateLimitService.isAllowed("u1", LlmService.RATE_LIMIT_OPERATION)).thenReturn(false);

        assertThrows(RateLimitExceededException.class, () -> service("openai").complete("u1", null, "hello", null));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void wrapsProviderFailures() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("401 invalid api key sk-..."));

        LlmUnavailableException e = assertThrows(LlmUnavailableException.class,
                () -> service("openai").complete("u1", null, "hello", null));
        assertEquals("AI service temporarily unavailable", e.getMessage());
    }

    @Test
    void openAiRequestsClampTheTokenBudgetAndUseLowReasoningEffort() {
        service("openai").complete("u1", null, "hello", 250);

        OpenAiChatOptions options = (OpenAiChatOptions) sentPrompt().getOptions();
        assertEquals(1024, options.getMaxCompletionTokens());
        assertEquals("low", options.getReasoningEffort());
        assertNull(options.getTemperature());
    }

    @Test
    void anthropicRequestsSendNoTemperatureAndCapTheBudget() {
        service("anthropic").complete("u1", null, "hello", 50_000);

        AnthropicChatOptions options = (AnthropicChatOptions) sentPrompt().getOptions();
        assertEquals(4096, options.getMaxTokens());
        assertNull(options.getTemperature());
        assertNotNull(options.getOutputConfig());
    }

    private Prompt sentPrompt() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        return captor.getValue();
    }

    private LlmService service(String provider) {
        if ("anthropic".equals(provider)) {
            when(chatModel.getOptions()).thenReturn(AnthropicChatOptions.builder().build());
        }
        return new LlmService(ChatClient.builder(chatModel), cacheRepository, rateLimitService, PROPERTIES,
                provider, "gpt-5.4-mini", "claude-opus-5-5");
    }

    private static ChatResponse reply(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().model("gpt-5.4-mini").usage(new DefaultUsage(10, 5)).build());
    }
}
