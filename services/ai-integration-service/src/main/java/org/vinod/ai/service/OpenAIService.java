package org.vinod.ai.service;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.vinod.ai.entity.AICache;
import org.vinod.ai.repository.AICacheRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIService {

    @Value("${openai.api.key}")
    private String openaiApiKey;

    private final AICacheRepository cacheRepository;
    private final RateLimitService rateLimitService;

    public String generateCompletion(String prompt, String userId) {
        return generateCompletion(prompt, userId, "gpt-3.5-turbo", 0.7, 1000);
    }

    public String generateCompletion(String prompt, String userId, String model, double temperature, int maxTokens) {
        // Check rate limit
        if (!rateLimitService.isAllowed(userId, "ai_completion")) {
            throw new RuntimeException("Rate limit exceeded. Please try again later.");
        }

        // Check cache first
        String cacheKey = generateCacheKey(prompt, model, temperature, maxTokens);
        Optional<AICache> cached = cacheRepository.findByCacheKey(cacheKey);
        if (cached.isPresent() && cached.get().getExpiresAt().isAfter(LocalDateTime.now())) {
            log.info("Returning cached AI response for user: {}", userId);
            return cached.get().getResponse();
        }

        try {
            OpenAiService service = new OpenAiService(openaiApiKey);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .messages(List.of(
                            new ChatMessage(ChatMessageRole.USER.value(), prompt)
                    ))
                    .build();

            ChatCompletionResult result = service.createChatCompletion(request);
            String response = result.getChoices().get(0).getMessage().getContent();

            // Cache the response
            cacheResponse(cacheKey, prompt, response, model, temperature, maxTokens);

            log.info("Generated AI completion for user: {}, tokens used: {}", userId,
                    result.getUsage().getTotalTokens());

            return response;

        } catch (Exception e) {
            log.error("Error calling OpenAI API", e);
            throw new RuntimeException("AI service temporarily unavailable: " + e.getMessage());
        }
    }

    public List<String> generateMultipleCompletions(String prompt, String userId, int count) {
        List<String> responses = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            try {
                String response = generateCompletion(prompt, userId + "_batch_" + i);
                responses.add(response);
            } catch (Exception e) {
                log.warn("Failed to generate completion {} for user: {}", i, userId, e);
                responses.add("Error generating response");
            }
        }

        return responses;
    }

    private String generateCacheKey(String prompt, String model, double temperature, int maxTokens) {
        String key = prompt + model + temperature + maxTokens;
        return String.valueOf(key.hashCode());
    }

    private void cacheResponse(String cacheKey, String prompt, String response, String model,
                             double temperature, int maxTokens) {
        try {
            LocalDateTime now = LocalDateTime.now();
            AICache cache = AICache.builder()
                    .cacheKey(cacheKey)
                    .prompt(prompt)
                    .response(response)
                    .model(model)
                    .createdAt(now)
                    .expiresAt(now.plusHours(1))
                    .build();

            cacheRepository.save(cache);
        } catch (Exception e) {
            log.warn("Failed to cache AI response", e);
        }
    }
}

