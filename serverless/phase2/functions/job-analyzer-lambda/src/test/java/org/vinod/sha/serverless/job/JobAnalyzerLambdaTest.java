package org.vinod.sha.serverless.job;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobAnalyzerLambdaTest {

    private JobAnalyzerLambda lambda;

    @BeforeEach
    void setUp() {
        lambda = new JobAnalyzerLambda();
    }

    @Test
    void testHealthEndpoint() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/health");

        APIGatewayProxyResponseEvent response = lambda.handleRequest(request, null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("UP");
        assertThat(response.getBody()).contains("job-analyzer-lambda");
    }

    @Test
    void testSuccessfulJobAnalysis() {
        String jsonBody = """
                {"jobId":"job-1001","title":"Senior Java Engineer","description":"We are hiring a senior Java engineer with Spring Boot, AWS and Docker experience for a hybrid backend platform role.","requiredSkills":["Java","Spring Boot","AWS"],"location":"Atlanta, GA","employmentType":"FULL_TIME"}
                """;

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/job/analyze")
                .withBody(jsonBody);

        APIGatewayProxyResponseEvent response = lambda.handleRequest(request, null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("SUCCESS");
        assertThat(response.getBody()).contains("job-1001");
        assertThat(response.getBody()).contains("SENIOR");
        assertThat(response.getBody()).contains("spring boot");
        assertThat(response.getBody()).contains("HYBRID");
    }

    @Test
    void testMissingTitle() {
        String jsonBody = "{" +
                "\"jobId\":\"job-1001\"," +
                "\"title\":null," +
                "\"description\":\"backend role\"}";

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/job/analyze")
                .withBody(jsonBody);

        APIGatewayProxyResponseEvent response = lambda.handleRequest(request, null);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("ERROR");
        assertThat(response.getBody()).contains("title");
    }

    @Test
    void testMissingDescription() {
        String jsonBody = "{" +
                "\"jobId\":\"job-1001\"," +
                "\"title\":\"Java Engineer\"," +
                "\"description\":null}";

        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/job/analyze")
                .withBody(jsonBody);

        APIGatewayProxyResponseEvent response = lambda.handleRequest(request, null);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("ERROR");
        assertThat(response.getBody()).contains("description");
    }

    @Test
    void testMissingRequestBody() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/job/analyze")
                .withBody(null);

        APIGatewayProxyResponseEvent response = lambda.handleRequest(request, null);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("Missing request body");
    }

    @Test
    void testInvalidJson() {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/job/analyze")
                .withBody("{invalid json}");

        APIGatewayProxyResponseEvent response = lambda.handleRequest(request, null);

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("Invalid JSON");
    }
}

