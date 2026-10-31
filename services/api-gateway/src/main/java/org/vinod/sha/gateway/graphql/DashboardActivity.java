package org.vinod.sha.gateway.graphql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The HR dashboard's recent activity, in words a recruiter reads: who uploaded a resume, who applied to which
 * job, who finished screening, and which jobs were posted here. Imported jobs are not listed one by one.
 * Unsorted: the caller normalises the services' timestamps, then sorts.
 */
final class DashboardActivity {

    private DashboardActivity() {
    }

    /**
     * @param candidates resume-parser candidate profiles (userId, name, createdAt)
     * @param sessions   screening sessions (candidateId, jobId, status, createdAt, completedAt)
     * @param jobTitles  job id → title for the sessions' jobs; a missing title reads "a job"
     * @param jobs       job-analyzer activity for jobs posted in the app (already worded)
     */
    static List<Map<String, Object>> build(List<Map<String, Object>> candidates,
                                           List<Map<String, Object>> sessions,
                                           Map<String, String> jobTitles,
                                           List<Map<String, Object>> jobs) {
        Map<String, String> names = new LinkedHashMap<>();
        candidates.forEach(c -> names.put(String.valueOf(c.get("userId")), name(c.get("name"), c.get("userId"))));

        List<Map<String, Object>> activity = new ArrayList<>(jobs);
        for (Map<String, Object> c : candidates) {
            activity.add(item("NEW_CANDIDATE", name(c.get("name"), c.get("userId")) + " uploaded a resume", c.get("createdAt")));
        }
        for (Map<String, Object> s : sessions) {
            String candidateId = String.valueOf(s.get("candidateId"));
            String who = names.getOrDefault(candidateId, "Candidate #" + candidateId);
            String job = jobTitles.getOrDefault(String.valueOf(s.get("jobId")), "a job");
            activity.add(item("APPLICATION", who + " applied to " + job, s.get("createdAt")));
            if ("COMPLETED".equalsIgnoreCase(String.valueOf(s.get("status")))) {
                String decision = Objects.toString(s.get("decision"), "");
                String outcome = decision.isBlank() || "PENDING".equalsIgnoreCase(decision) ? "" : " (" + decision.toLowerCase() + ")";
                activity.add(item("SCREENING_COMPLETED", who + " completed screening for " + job + outcome,
                        s.get("completedAt") != null ? s.get("completedAt") : s.get("updatedAt")));
            }
        }
        return activity;
    }

    private static String name(Object name, Object userId) {
        String n = Objects.toString(name, "").trim();
        return n.isEmpty() ? "Candidate #" + userId : n;
    }

    private static Map<String, Object> item(String type, String description, Object timestamp) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("description", description);
        item.put("timestamp", timestamp == null ? "" : String.valueOf(timestamp));
        return item;
    }
}
