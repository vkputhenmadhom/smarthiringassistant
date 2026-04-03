package org.vinod.sha.serverless.job;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Named;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Named("jobAnalyzer")
public class JobAnalyzerLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Map<String, String> JSON_HEADERS = Map.of(
            "Content-Type", "application/json",
            "Access-Control-Allow-Origin", "*"
    );

    private static final List<String> KNOWN_SKILLS = List.of(
            "java",
            "spring boot",
            "microservices",
            "aws",
            "docker",
            "kubernetes",
            "graphql",
            "postgresql",
            "mongodb",
            "react",
            "angular"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            if (isHealthCheck(input)) {
                return response(200, objectMapper.writeValueAsString(Map.of(
                        "status", "UP",
                        "service", "job-analyzer-lambda"
                )));
            }

            if (input == null || input.getBody() == null || input.getBody().isBlank()) {
                return badRequest("Missing request body");
            }

            JobAnalyzeRequest request = objectMapper.readValue(input.getBody(), JobAnalyzeRequest.class);
            if (!request.isValid()) {
                return badRequest("Fields 'title' and 'description' are required");
            }

            JobAnalyzeResponse analyzed = analyzeJob(request);
            return response(200, objectMapper.writeValueAsString(Map.of(
                    "status", "SUCCESS",
                    "data", analyzed
            )));
        } catch (JsonProcessingException e) {
            return badRequest("Invalid JSON payload");
        } catch (Exception e) {
            return error("Unexpected server error");
        }
    }

    private JobAnalyzeResponse analyzeJob(JobAnalyzeRequest request) {
        String combinedText = (request.title() + " " + request.description()).toLowerCase(Locale.ROOT);
        List<String> extractedSkills = extractSkills(combinedText, request.requiredSkills());
        String seniorityLevel = detectSeniority(combinedText);
        String workMode = detectWorkMode(combinedText);
        String summary = buildSummary(request.title(), seniorityLevel, extractedSkills, workMode);

        return new JobAnalyzeResponse(
                request.jobId(),
                seniorityLevel,
                extractedSkills,
                workMode,
                summary,
                "ANALYZED"
        );
    }

    private List<String> extractSkills(String combinedText, List<String> requiredSkills) {
        Set<String> skills = new LinkedHashSet<>();

        for (String knownSkill : KNOWN_SKILLS) {
            if (combinedText.contains(knownSkill)) {
                skills.add(knownSkill);
            }
        }

        if (requiredSkills != null) {
            for (String skill : requiredSkills) {
                if (skill != null && !skill.isBlank()) {
                    skills.add(skill.trim().toLowerCase(Locale.ROOT));
                }
            }
        }

        return new ArrayList<>(skills);
    }

    private String detectSeniority(String combinedText) {
        if (combinedText.contains("principal") || combinedText.contains("staff engineer")) {
            return "PRINCIPAL";
        }
        if (combinedText.contains("lead") || combinedText.contains("senior")) {
            return "SENIOR";
        }
        if (combinedText.contains("junior") || combinedText.contains("entry level") || combinedText.contains("graduate")) {
            return "JUNIOR";
        }
        if (combinedText.contains("intern")) {
            return "INTERN";
        }
        return "MID";
    }

    private String detectWorkMode(String combinedText) {
        if (combinedText.contains("hybrid")) {
            return "HYBRID";
        }
        if (combinedText.contains("remote")) {
            return "REMOTE";
        }
        if (combinedText.contains("on-site") || combinedText.contains("onsite") || combinedText.contains("office")) {
            return "ONSITE";
        }
        return "UNSPECIFIED";
    }

    private String buildSummary(String title, String seniorityLevel, List<String> extractedSkills, String workMode) {
        String skillSummary = extractedSkills.isEmpty() ? "general software engineering skills" : String.join(", ", extractedSkills);
        return String.format(
                "%s role classified as %s with %s work mode. Key skills detected: %s.",
                title,
                seniorityLevel,
                workMode,
                skillSummary
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
        return "GET".equalsIgnoreCase(method)
                && ("/health".equals(path) || "/health".equals(resource));
    }
}

