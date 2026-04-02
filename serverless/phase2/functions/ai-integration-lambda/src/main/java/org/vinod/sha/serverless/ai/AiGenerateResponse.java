package org.vinod.sha.serverless.ai;

/**
 * Response contract for AI generation endpoint.
 * Simple JSON-based contract for API Gateway proxy.
 */
public record AiGenerateResponse(
        String generatedContent,
        String model,
        Integer tokensUsed,
        String generatedAt
) {
}

