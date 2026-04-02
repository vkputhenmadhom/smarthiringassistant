package org.vinod.sha.serverless.resume;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeParserLambdaTest {

    private final ResumeParserLambda lambda = new ResumeParserLambda();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldReturnHealthForGetHealthRequest() throws Exception {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/health")
                .withResource("/health");

        APIGatewayProxyResponseEvent response = lambda.handleRequest(request, null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        JsonNode body = mapper.readTree(response.getBody());
        assertThat(body.path("status").asText()).isEqualTo("UP");
        assertThat(body.path("service").asText()).isEqualTo("resume-parser-lambda");
    }

    @Test
    void shouldReturn200WithParsedDataForValidProxyRequest() throws Exception {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/resume/parse")
                .withResource("/resume/parse")
                .withBody("""
                        {
                          \"candidateId\": \"cand-1001\",
                          \"fileName\": \"resume.txt\",
                          \"contentType\": \"text/plain\",
                          \"content\": \"Email: vinod@example.com Skills: Java, Spring Boot, React Experience: 6 years\"
                        }
                        """);

        APIGatewayProxyResponseEvent response = lambda.handleRequest(request, null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getHeaders()).containsEntry("Content-Type", "application/json");

        JsonNode body = mapper.readTree(response.getBody());
        assertThat(body.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(body.path("data").path("candidateId").asText()).isEqualTo("cand-1001");
        assertThat(body.path("data").path("parseStatus").asText()).isEqualTo("PARSED");
    }

    @Test
    void shouldReturn400WhenContentMissing() throws Exception {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/resume/parse")
                .withResource("/resume/parse")
                .withBody("""
                        {
                          \"candidateId\": \"cand-1002\",
                          \"fileName\": \"resume.txt\",
                          \"contentType\": \"text/plain\",
                          \"content\": \"\"
                        }
                        """);

        APIGatewayProxyResponseEvent response = lambda.handleRequest(request, null);

        assertThat(response.getStatusCode()).isEqualTo(400);
        JsonNode body = mapper.readTree(response.getBody());
        assertThat(body.path("status").asText()).isEqualTo("ERROR");
        assertThat(body.path("error").asText()).contains("content");
    }

    @Test
    void shouldReturn400ForMalformedJson() throws Exception {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/resume/parse")
                .withResource("/resume/parse")
                .withBody("{not-json}");

        APIGatewayProxyResponseEvent response = lambda.handleRequest(request, null);

        assertThat(response.getStatusCode()).isEqualTo(400);
        JsonNode body = mapper.readTree(response.getBody());
        assertThat(body.path("status").asText()).isEqualTo("ERROR");
        assertThat(body.path("error").asText()).contains("Invalid JSON");
    }
}
