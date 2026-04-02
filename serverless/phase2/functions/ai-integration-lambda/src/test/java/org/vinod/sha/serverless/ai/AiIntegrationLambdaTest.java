package org.vinod.sha.serverless.ai;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiIntegrationLambdaTest {

    private AiIntegrationLambda lambda;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        lambda = new AiIntegrationLambda();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testHealthEndpoint() throws Exception {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/health");

        APIGatewayProxyResponseEvent response = lambda.handleRequest(request, null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("UP");
        assertThat(response.getBody()).contains("ai-integration-lambda");
    }

    @Test
    void testSuccessfulAiGeneration() throws Exception {
        AiGenerateRequest aiRequest = new AiGenerateRequest(
                "Generate interview questions for Java developer",
                "gpt-4",
                100
        );

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/ai/generate")
                .withBody(objectMapper.writeValueAsString(aiRequest));

        APIGatewayProxyResponseEvent response = lambda.handleRequest(request, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("SUCCESS");
    }

    @Test
    void testMissingPrompt() throws Exception {
        String jsonBody = "{\"prompt\":null,\"model\":\"gpt-4\",\"maxTokens\":100}";

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/ai/generate")
                .withBody(jsonBody);

        APIGatewayProxyResponseEvent response = lambda.handleRequest(request, null);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("ERROR");
    }

    @Test
    void testMissingModel() throws Exception {
        AiGenerateRequest aiRequest = new AiGenerateRequest(
                "Generate interview questions",
                null,
                100
        );

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/ai/generate")
                .withBody(objectMapper.writeValueAsString(aiRequest));

        APIGatewayProxyResponseEvent response = lambda.handleRequest(request, null);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("ERROR");
    }

    @Test
    void testMissingRequestBody() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/ai/generate")
                .withBody(null);

        APIGatewayProxyResponseEvent response = lambda.handleRequest(request, null);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("Missing request body");
    }

    @Test
    void testInvalidJson() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/ai/generate")
                .withBody("{invalid json}");

        APIGatewayProxyResponseEvent response = lambda.handleRequest(request, null);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("Invalid JSON");
    }
}

