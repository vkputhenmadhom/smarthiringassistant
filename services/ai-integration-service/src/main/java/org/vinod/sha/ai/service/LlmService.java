package org.vinod.sha.ai.service;

import com.anthropic.models.messages.OutputConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vinod.sha.ai.config.AiProperties;
import org.vinod.sha.ai.entity.AICache;
import org.vinod.sha.ai.exception.LlmUnavailableException;
import org.vinod.sha.ai.exception.RateLimitExceededException;
import org.vinod.sha.ai.repository.AICacheRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Provider-neutral LLM access through Spring AI's {@link ChatClient}. The provider is chosen with
 * {@code spring.ai.model.chat} ({@code AI_PROVIDER}); the model, limits and effort are server-side.
 */
@Slf4j
@Service
public class LlmService {

    static final String RATE_LIMIT_OPERATION = "ai_completion";

    public record Completion(String text, String model, Integer tokensUsed, boolean cached) {
    }

    private final ChatClient chatClient;
    private final AICacheRepository cacheRepository;
    private final RateLimitService rateLimitService;
    private final AiProperties properties;
    private final String provider;
    private final String configuredModel;

    public LlmService(ChatClient.Builder chatClientBuilder,
                      AICacheRepository cacheRepository,
                      RateLimitService rateLimitService,
                      AiProperties properties,
                      @Value("${spring.ai.model.chat:openai}") String provider,
                      @Value("${spring.ai.openai.chat.options.model:}") String openAiModel,
                      @Value("${spring.ai.anthropic.chat.options.model:}") String anthropicModel) {
        this.chatClient = chatClientBuilder.build();
        this.cacheRepository = cacheRepository;
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.provider = provider;
        this.configuredModel = "anthropic".equals(provider) ? anthropicModel : openAiModel;
    }

    /**
     * @param systemPrompt instructions; untrusted content belongs in {@code userPrompt} only
     */
    public Completion complete(String userId, String systemPrompt, String userPrompt, Integer requestedMaxTokens) {
        if (userPrompt.length() > properties.maxPromptChars()) {
            throw new IllegalArgumentException("prompt exceeds " + properties.maxPromptChars() + " characters");
        }
        int maxTokens = properties.clampOutputTokens(requestedMaxTokens);
        String cacheKey = cacheKey(systemPrompt, userPrompt, maxTokens);

        Optional<AICache> cached = cacheRepository.findByCacheKey(cacheKey)
                .filter(entry -> entry.getExpiresAt().isAfter(LocalDateTime.now()));
        if (cached.isPresent()) {
            return new Completion(cached.get().getResponse(), cached.get().getModel(), null, true);
        }

        // Only provider calls count against the quota; cache hits are free.
        if (!rateLimitService.isAllowed(userId, RATE_LIMIT_OPERATION)) {
            throw new RateLimitExceededException();
        }

        ChatResponse response;
        try {
            ChatClient.ChatClientRequestSpec request = chatClient.prompt();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                request = request.system(systemPrompt);
            }
            response = request.user(userPrompt).options(optionsFor(maxTokens)).call().chatResponse();
        } catch (RuntimeException e) {
            throw new LlmUnavailableException(e);
        }
        if (response == null || response.getResult() == null) {
            throw new LlmUnavailableException(new IllegalStateException("empty response from " + provider));
        }

        String text = Optional.ofNullable(response.getResult().getOutput().getText()).orElse("");
        String model = Optional.ofNullable(response.getMetadata().getModel()).filter(m -> !m.isBlank()).orElse(configuredModel);
        Usage usage = response.getMetadata().getUsage();
        Integer tokensUsed = usage == null ? null : usage.getTotalTokens();
        log.info("LLM completion for user={} provider={} model={} tokens={}", userId, provider, model, tokensUsed);

        save(cacheKey, userPrompt, text, model);
        return new Completion(text, model, tokensUsed, false);
    }

    /**
     * Short extraction and generation tasks: low reasoning effort keeps them fast and cheap.
     * No temperature: current OpenAI reasoning models and Claude Opus 5.5 reject custom sampling values.
     */
    private ChatOptions.Builder<?> optionsFor(int maxTokens) {
        if ("anthropic".equals(provider)) {
            return AnthropicChatOptions.builder()
                    .maxTokens(maxTokens)
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build());
        }
        return OpenAiChatOptions.builder()
                .maxCompletionTokens(maxTokens)
                .reasoningEffort("low");
    }

    /** SHA-256 over everything that shapes the answer, so different requests never share an entry. */
    private String cacheKey(String systemPrompt, String userPrompt, int maxTokens) {
        String material = String.join("\u0000", provider, configuredModel,
                systemPrompt == null ? "" : systemPrompt, userPrompt, Integer.toString(maxTokens));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private void save(String cacheKey, String prompt, String response, String model) {
        try {
            LocalDateTime now = LocalDateTime.now();
            cacheRepository.save(AICache.builder()
                    .cacheKey(cacheKey)
                    .prompt(prompt)
                    .response(response)
                    .model(model)
                    .createdAt(now)
                    .expiresAt(now.plusMinutes(properties.cacheTtlMinutes()))
                    .build());
        } catch (RuntimeException e) {
            log.warn("Failed to cache AI response: {}", e.getMessage());
        }
    }
}
