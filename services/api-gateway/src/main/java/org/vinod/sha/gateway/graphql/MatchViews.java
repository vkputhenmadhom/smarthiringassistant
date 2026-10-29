package org.vinod.sha.gateway.graphql;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shapes candidate-matcher's MatchResponse into the GraphQL {@code CandidateMatch} and {@code MatchPage} types. */
public final class MatchViews {

    private MatchViews() {
    }

    /** The matcher scores 0–100; GraphQL scores are 0–1 (the dashboards show {@code score * 100}%). */
    public static double score(Object raw) {
        if (!(raw instanceof Number n)) {
            return 0.0;
        }
        double value = n.doubleValue();
        double normalized = value > 1.0 ? value / 100.0 : value;
        return Math.max(0.0, Math.min(1.0, normalized));
    }

    /**
     * The matcher now stores the GraphQL statuses. NEW/REVIEWED/ACCEPTED can still arrive in events queued before
     * the change, so they are mapped rather than passed through.
     */
    public static String status(Object raw) {
        String status = raw == null ? "" : String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
        return switch (status) {
            case "PENDING", "MATCHED", "SHORTLISTED", "REJECTED", "HIRED" -> status;
            case "NEW", "REVIEWED" -> "MATCHED";
            case "ACCEPTED" -> "SHORTLISTED";
            default -> "PENDING";
        };
    }

    static Map<String, Object> match(Map<String, Object> raw) {
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("id", raw.get("matchId"));
        match.put("candidateId", String.valueOf(raw.get("candidateId")));
        match.put("jobId", raw.get("jobId"));
        match.put("score", score(raw.get("overallScore")));
        match.put("status", status(raw.get("status")));
        match.put("skillMatches", stringList(raw.get("matchedSkills")));
        match.put("skillGaps", stringList(raw.get("missingSkills")));
        match.put("matchedAt", raw.get("createdAt"));
        return match;
    }

    /** The matcher returns every match for a job or candidate; the gateway pages them, best score first. */
    static Map<String, Object> page(List<Map<String, Object>> raw, int page, int size) {
        int safeSize = Math.max(1, size);
        int safePage = Math.max(0, page);
        List<Map<String, Object>> all = raw.stream()
                .map(MatchViews::match)
                .sorted(Comparator.comparingDouble((Map<String, Object> m) -> (double) m.get("score")).reversed())
                .toList();
        int from = Math.min(all.size(), safePage * safeSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", all.subList(from, Math.min(all.size(), from + safeSize)));
        result.put("totalElements", all.size());
        result.put("totalPages", (all.size() + safeSize - 1) / safeSize);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    private static List<String> stringList(Object raw) {
        return raw instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }
}
