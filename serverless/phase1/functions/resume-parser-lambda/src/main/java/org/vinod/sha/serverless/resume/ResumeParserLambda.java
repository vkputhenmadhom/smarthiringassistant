package org.vinod.sha.serverless.resume;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import jakarta.inject.Named;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Named("resumeParser")
public class ResumeParserLambda implements RequestHandler<ResumeParseRequest, ResumeParseResponse> {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern EXPERIENCE_PATTERN = Pattern.compile("(\\d+)\\s*(?:\\+)?\\s*years?", Pattern.CASE_INSENSITIVE);

    @Override
    public ResumeParseResponse handleRequest(ResumeParseRequest input, Context context) {
        if (input == null || input.content() == null || input.content().isBlank()) {
            return new ResumeParseResponse(
                    input == null ? "unknown" : input.candidateId(),
                    null,
                    0,
                    List.of(),
                    "FAILED"
            );
        }

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
}

