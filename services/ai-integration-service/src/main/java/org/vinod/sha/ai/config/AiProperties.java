package org.vinod.sha.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Server-side limits for AI requests. Callers cannot pick the model or exceed these bounds.
 *
 * @param maxPromptChars     longest accepted prompt
 * @param minOutputTokens    floor for the output budget; reasoning models spend part of it before answering
 * @param maxOutputTokens    ceiling for the output budget (cost guard)
 * @param maxBatchSize       most completions one /completions call may request
 * @param cacheTtlMinutes    how long identical requests are answered from the cache
 */
@ConfigurationProperties("ai")
public record AiProperties(int maxPromptChars, int minOutputTokens, int maxOutputTokens,
                           int maxBatchSize, long cacheTtlMinutes) {

    public int clampOutputTokens(Integer requested) {
        int value = requested == null ? maxOutputTokens : requested;
        return Math.clamp(value, minOutputTokens, maxOutputTokens);
    }

    public int clampBatchSize(int requested) {
        return Math.clamp(requested, 1, maxBatchSize);
    }
}
