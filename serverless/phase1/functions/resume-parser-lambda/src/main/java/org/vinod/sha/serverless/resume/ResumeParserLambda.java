package org.vinod.sha.serverless.resume;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Named;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Named("resumeParser")
public class ResumeParserLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern EXPERIENCE_PATTERN = Pattern.compile("(\\d+)\\s*(?:\\+)?\\s*years?", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> JSON_HEADERS = Map.of(
            "Content-Type", "application/json",
            "Access-Control-Allow-Origin", "*"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        try {
            if (isHealthCheck(input)) {
                String body = objectMapper.writeValueAsString(Map.of(
                        "status", "UP",
                        "service", "resume-parser-lambda"
                ));
                return response(200, body);
            }

            if (input == null || input.getBody() == null || input.getBody().isBlank()) {
                return badRequest("Missing request body");
            }

            ResumeParseRequest request = objectMapper.readValue(input.getBody(), ResumeParseRequest.class);
            if (request.content() == null || request.content().isBlank()) {
                return badRequest("Field 'content' is required");
            }

            ResumeParseResponse parsed = parseResume(request);
            String body = objectMapper.writeValueAsString(Map.of(
                    "status", "SUCCESS",
                    "data", parsed
            ));
            return response(200, body);
        } catch (JsonProcessingException e) {
            return badRequest("Invalid JSON payload");
        } catch (Exception e) {
            return error("Unexpected server error");
        }
    }

    private ResumeParseResponse parseResume(ResumeParseRequest input) {
        String content = input.content();
        String email = findFirstMatch(EMAIL_PATTERN, content);
        int experienceYears = parseExperienceYears(content);
        List<String> skills = extractSkills(content);

        return new ResumeParseResponse(
                input.candidateId(),
                email,
                experienceYears,
                skills,
                "PARSED"
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

    private static String findFirstMatch(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group() : null;
    }

    private static int parseExperienceYears(String input) {
        Matcher matcher = EXPERIENCE_PATTERN.matcher(input);
        if (!matcher.find()) {
            return 0;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static List<String> extractSkills(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        List<String> extracted = new ArrayList<>();

        addSkillIfPresent(extracted, lower, "java");
        addSkillIfPresent(extracted, lower, "spring boot");
        addSkillIfPresent(extracted, lower, "angular");
        addSkillIfPresent(extracted, lower, "react");
        addSkillIfPresent(extracted, lower, "graphql");
        addSkillIfPresent(extracted, lower, "aws");

        return extracted;
    }

    private static void addSkillIfPresent(List<String> skills, String content, String skill) {
        if (content.contains(skill)) {
            skills.add(skill);
        }
    }

    private static boolean isHealthCheck(APIGatewayProxyRequestEvent input) {
        if (input == null) {
            return false;
        }
        String method = input.getHttpMethod();
        String path = input.getPath();
        String resource = input.getResource();
        return "GET".equalsIgnoreCase(method) && ("/health".equals(path) || "/health".equals(resource));
    }
}
