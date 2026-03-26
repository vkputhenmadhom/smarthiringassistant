package org.vinod.ai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vinod.ai.dto.AIRequest;
import org.vinod.ai.dto.AIResponse;
import org.vinod.ai.service.OpenAIService;
import org.vinod.ai.service.RateLimitService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class AIController {

    private final OpenAIService openAIService;
    private final RateLimitService rateLimitService;

    @PostMapping("completion")
    public ResponseEntity<AIResponse> generateCompletion(@RequestBody AIRequest request) {
        log.info("AI completion request for user: {}", request.getUserId());

        try {
            String response = openAIService.generateCompletion(
                    request.getPrompt(),
                    request.getUserId(),
                    request.getModel() != null ? request.getModel() : "gpt-3.5-turbo",
                    request.getTemperature() != null ? request.getTemperature() : 0.7,
                    request.getMaxTokens() != null ? request.getMaxTokens() : 1000
            );

            return ResponseEntity.ok(AIResponse.builder()
                    .response(response)
                    .model(request.getModel())
                    .tokensUsed(estimateTokens(response))
                    .build());

        } catch (Exception e) {
            log.error("Error generating AI completion", e);
            return ResponseEntity.internalServerError()
                    .body(AIResponse.builder()
                            .error("AI service temporarily unavailable")
                            .build());
        }
    }

    @PostMapping("completions")
    public ResponseEntity<List<String>> generateMultipleCompletions(
            @RequestBody AIRequest request,
            @RequestParam(defaultValue = "3") int count) {

        log.info("Multiple AI completions request for user: {}, count: {}", request.getUserId(), count);

        try {
            List<String> responses = openAIService.generateMultipleCompletions(
                    request.getPrompt(),
                    request.getUserId(),
                    count
            );

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("Error generating multiple AI completions", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("rate-limit/{userId}")
    public ResponseEntity<Long> getRemainingTokens(@PathVariable String userId) {
        long remaining = rateLimitService.getRemainingTokens(userId, "ai_completion");
        return ResponseEntity.ok(remaining);
    }

    @PostMapping("analyze-resume")
    public ResponseEntity<AIResponse> analyzeResume(@RequestBody AIRequest request) {
        log.info("Resume analysis request for user: {}", request.getUserId());

        String analysisPrompt = """
                Analyze the following resume and provide:
                1. Key skills and technologies
                2. Years of experience
                3. Career progression summary
                4. Strengths and areas for improvement
                5. Recommended job titles

                Resume:
                """ + request.getPrompt();

        try {
            String analysis = openAIService.generateCompletion(analysisPrompt, request.getUserId());
            return ResponseEntity.ok(AIResponse.builder()
                    .response(analysis)
                    .model("gpt-3.5-turbo")
                    .build());

        } catch (Exception e) {
            log.error("Error analyzing resume", e);
            return ResponseEntity.internalServerError()
                    .body(AIResponse.builder()
                            .error("Resume analysis failed")
                            .build());
        }
    }

    private int estimateTokens(String text) {
        // Rough estimation: 1 token ≈ 4 characters
        return text.length() / 4;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        log.error("Error in AI controller", e);
        return ResponseEntity.status(500).body("Internal server error: " + e.getMessage());
    }
}

