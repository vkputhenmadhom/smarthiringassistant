package org.vinod.sha.ai.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.vinod.sha.ai.config.AiProperties;
import org.vinod.sha.ai.dto.AIRequest;
import org.vinod.sha.ai.dto.AIResponse;
import org.vinod.sha.ai.exception.RateLimitExceededException;
import org.vinod.sha.ai.service.LlmService;
import org.vinod.sha.ai.service.RateLimitService;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal AI endpoint for other services (interview-prep, job-analyzer). It is not routed through
 * the API gateway and has no published port, so the caller-supplied userId is trusted for quotas.
 */
@Slf4j
@RestController
@RequestMapping("/")
public class AIController {

    /** Instructions for /analyze-resume (ai-prompts.yml): structured JSON for resume-parser */
    private final String resumeAnalysisInstructions;

    private final LlmService llmService;
    private final RateLimitService rateLimitService;
    private final AiProperties properties;

    public AIController(LlmService llmService, RateLimitService rateLimitService, AiProperties properties,
                        @Value("${ai.prompts.resume-analysis}") String resumeAnalysisInstructions) {
        this.llmService = llmService;
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.resumeAnalysisInstructions = resumeAnalysisInstructions;
    }

    @PostMapping("completion")
    public AIResponse generateCompletion(@Valid @RequestBody AIRequest request) {
        return toResponse(llmService.complete(request.getUserId(), null, request.getPrompt(), request.getMaxTokens()));
    }

    /**
     * Up to {@code ai.max-batch-size} completions. Each one counts against the caller's quota; the
     * batch stops early (returning what it has) once the quota is used up.
     */
    @PostMapping("completions")
    public ResponseEntity<List<String>> generateMultipleCompletions(@Valid @RequestBody AIRequest request,
                                                                    @RequestParam(defaultValue = "3") int count) {
        int batchSize = properties.clampBatchSize(count);
        List<String> responses = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            try {
                responses.add(llmService.complete(request.getUserId(), null, request.getPrompt(), request.getMaxTokens()).text());
            } catch (RateLimitExceededException e) {
                if (responses.isEmpty()) {
                    throw e;
                }
                log.info("Batch for user={} stopped at {}/{}: rate limit reached", request.getUserId(), i, batchSize);
                break;
            }
        }
        return ResponseEntity.ok(responses);
    }

    @GetMapping("rate-limit/{userId}")
    public long getRemainingTokens(@PathVariable String userId) {
        return rateLimitService.getRemainingTokens(userId, "ai_completion");
    }

    /**
     * The resume's details as one JSON object (see ai-prompts.yml). Never cached: the cache keeps prompts in
     * plain text, and a resume is personal data that resume-parser stores encrypted.
     */
    @PostMapping("analyze-resume")
    public AIResponse analyzeResume(@Valid @RequestBody AIRequest request) {
        String resume = "<resume>\n" + request.getPrompt() + "\n</resume>";
        return toResponse(llmService.complete(request.getUserId(), resumeAnalysisInstructions, resume, request.getMaxTokens(), false));
    }

    private static AIResponse toResponse(LlmService.Completion completion) {
        return AIResponse.builder()
                .response(completion.text())
                .model(completion.model())
                .tokensUsed(completion.tokensUsed())
                .cached(completion.cached())
                .build();
    }
}
