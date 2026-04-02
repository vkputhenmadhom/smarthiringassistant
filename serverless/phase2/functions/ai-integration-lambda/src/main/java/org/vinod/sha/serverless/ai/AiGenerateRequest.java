package org.vinod.sha.serverless.ai;

/**
 * Request contract for AI generation endpoint.
 * Simple JSON-based contract for API Gateway proxy.
 */
public record AiGenerateRequest(
        String prompt,
        String model,
        Integer maxTokens
) {
    /**
     * Validate required fields.
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return prompt != null && !prompt.isBlank() &&
               model != null && !model.isBlank();
    }
}

