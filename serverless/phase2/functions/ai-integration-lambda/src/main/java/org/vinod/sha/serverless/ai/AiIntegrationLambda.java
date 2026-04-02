package org.vinod.sha.serverless.ai;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Named;

import java.time.Instant;
import java.util.Map;

/**
 * AI Integration Lambda - Proxy handler for /ai/generate endpoint.
 *
 * This is a stateless, short-lived service that:
 * - Accepts AI generation requests (prompt, model, maxTokens)
 * - Returns mocked AI responses (for demo, can wire to OpenAI/Claude)
 * - Supports health checks
 * - No external service dependencies (independently deployable)
 */
@Named("aiIntegration")
public class AiIntegrationLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Map<String, String> JSON_HEADERS = Map.of(
            "Content-Type", "application/json",
            "Access-Control-Allow-Origin", "*"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            // Health check endpoint
            if (isHealthCheck(input)) {
                String body = objectMapper.writeValueAsString(Map.of(
                        "status", "UP",
                        "service", "ai-integration-lambda"
                ));
                return response(200, body);
            }

            // Validate request body
            if (input == null || input.getBody() == null || input.getBody().isBlank()) {
                return badRequest("Missing request body");
            }

            // Parse request
            AiGenerateRequest request = objectMapper.readValue(input.getBody(), AiGenerateRequest.class);
            if (!request.isValid()) {
                return badRequest("Fields 'prompt' and 'model' are required");
            }

            // Generate response (mocked for demo)
            AiGenerateResponse generated = generateContent(request);
            String body = objectMapper.writeValueAsString(Map.of(
                    "status", "SUCCESS",
                    "data", generated
            ));
            return response(200, body);

        } catch (JsonProcessingException e) {
            return badRequest("Invalid JSON payload");
        } catch (Exception e) {
            return error("Unexpected server error: " + e.getMessage());
        }
    }

    /**
     * Generate AI content (mocked for demo).
     * In production, wire to OpenAI, Claude, or other AI service here.
     */
    private AiGenerateResponse generateContent(AiGenerateRequest request) {
        // Mock response for demo
        String mockContent = String.format(
                "Generated content for prompt: '%s' using model '%s'",
                request.prompt().substring(0, Math.min(50, request.prompt().length())),
                request.model()
        );

        return new AiGenerateResponse(
                mockContent,
                request.model(),
                request.maxTokens() != null ? request.maxTokens() : 100,
                Instant.now().toString()
        );
    }

    private APIGatewayProxyResponseEvent badRequest(String message) {
        try {
            return response(400, objectMapper.writeValueAsString(Map.of(
                    "status", "ERROR",
                    "error", message
            )));
        } catch (JsonProcessingException ex) {
            return response(400, "{\"status\":\"ERROR\",\"error\":\"Bad request\"}");
        }
    }

    private APIGatewayProxyResponseEvent error(String message) {
        try {
            return response(500, objectMapper.writeValueAsString(Map.of(
                    "status", "ERROR",
                    "error", message
            )));
        } catch (JsonProcessingException ex) {
            return response(500, "{\"status\":\"ERROR\",\"error\":\"Internal server error\"}");
        }
    }

    private APIGatewayProxyResponseEvent response(int statusCode, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(JSON_HEADERS)
                .withBody(body);
    }

    private static boolean isHealthCheck(APIGatewayProxyRequestEvent input) {
        if (input == null) {
            return false;
        }
        String method = input.getHttpMethod();
        String path = input.getPath();
        String resource = input.getResource();
        return "GET".equalsIgnoreCase(method) &&
               ("/health".equals(path) || "/health".equals(resource));
    }
}

