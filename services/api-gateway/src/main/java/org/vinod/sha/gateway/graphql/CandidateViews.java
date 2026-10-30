package org.vinod.sha.gateway.graphql;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Shapes resume-parser's CandidateProfile into the GraphQL {@code Candidate} type. */
final class CandidateViews {

    private CandidateViews() {
    }

    /**
     * @param screeningStatus the GraphQL ScreeningStatus, or null when the screening service could not be asked
     * @param matchScore      best match score 0–1, or null when unknown
     */
    static Map<String, Object> candidate(Map<String, Object> profile, String screeningStatus, Double matchScore) {
        String id = String.valueOf(profile.get("userId"));
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", id);
        c.put("userId", id);
        c.put("name", Objects.toString(profile.get("name"), "Candidate #" + id));
        c.put("email", Objects.toString(profile.get("email"), ""));
        c.put("phone", profile.get("phone"));
        c.put("linkedInUrl", null);
        c.put("githubUrl", null);
        c.put("skills", list(profile.get("skills")));
        c.put("experience", list(profile.get("experience")).stream().map(CandidateViews::experience).toList());
        c.put("education", list(profile.get("education")));
        c.put("resumeUrl", null);
        c.put("parseStatus", Objects.toString(profile.get("parseStatus"), "PENDING"));
        c.put("screeningStatus", screeningStatus);
        c.put("matchScore", matchScore);
        c.put("createdAt", profile.get("createdAt"));
        c.put("updatedAt", profile.get("updatedAt"));
        return c;
    }

    /**
     * Each candidate's screening status, from their most recent session: IN_PROGRESS, or once COMPLETED,
     * PASSED/FAILED by decision. Candidates with no session are absent (NOT_STARTED).
     */
    static Map<String, String> screeningStatuses(List<Map<String, Object>> sessions) {
        return sessions.stream()
                .filter(s -> s.get("candidateId") != null)
                .collect(Collectors.groupingBy(s -> String.valueOf(s.get("candidateId")),
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(s -> Objects.toString(s.get("createdAt"), ""))),
                                latest -> latest.map(CandidateViews::screeningStatus).orElse("NOT_STARTED"))));
    }

    static String screeningStatus(Map<String, Object> session) {
        if (!"COMPLETED".equalsIgnoreCase(Objects.toString(session.get("status"), ""))) {
            return "IN_PROGRESS";
        }
        return switch (Objects.toString(session.get("decision"), "").toUpperCase()) {
            case "PASS" -> "PASSED";
            case "FAIL" -> "FAILED";
            default -> "IN_PROGRESS";
        };
    }

    static <T> Map<String, T> byId(List<T> items, Function<T, Object> id) {
        Map<String, T> map = new LinkedHashMap<>();
        items.forEach(item -> map.put(String.valueOf(id.apply(item)), item));
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> experience(Object raw) {
        Map<String, Object> job = new LinkedHashMap<>((Map<String, Object>) raw);
        job.putIfAbsent("technologies", List.of());
        return job;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> list(Object raw) {
        return raw instanceof List<?> l ? (List<T>) l : List.of();
    }
}
