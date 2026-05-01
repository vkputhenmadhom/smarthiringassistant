package org.vinod.sha.gateway.graphql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.client.WebClient;
import org.vinod.sha.gateway.resilience.GatewayResilience;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * GraphQL resolver that acts as a BFF (Backend For Frontend) aggregating
 * calls to the individual microservices via their REST APIs.
 */
@Slf4j
@Controller
public class JobGraphQLResolver {

    private static final String JOB_SERVICE_BASE = "http://job-analyzer-service:8005/api/jobs";
    private static final String JOB_BACKEND = "job-analyzer-service";

    private final WebClient.Builder webClientBuilder;
    private final GatewayResilience gatewayResilience;

    public JobGraphQLResolver(WebClient.Builder webClientBuilder,
                              GatewayResilience gatewayResilience) {
        this.webClientBuilder = webClientBuilder;
        this.gatewayResilience = gatewayResilience;
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    @QueryMapping
    public Mono<Map<String, Object>> jobs(
            @Argument Integer page,
            @Argument Integer size,
            @Argument String status,
            @Argument String search,
            @Argument String source) {
        log.debug("GraphQL jobs query: page={}, size={}, status={}, search={}, source={}", page, size, status, search, source);
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;

        String uri = buildJobsUri(p, s, status, search, source);
        return requestMap(uri)
                .map(this::normalizeJobPage)
                .defaultIfEmpty(emptyPage())
                .onErrorResume(e -> {
                    log.warn("job-analyzer-service unreachable: {}", e.getMessage());
                    return Mono.just(emptyPage());
                });
    }

    @QueryMapping
    public Mono<Map<String, Object>> job(@Argument String id) {
        log.debug("GraphQL job query: id={}", id);
        return requestMap(JOB_SERVICE_BASE + "/jobs/" + id)
                .map(this::normalizeJob)
                .onErrorResume(e -> {
                    log.warn("job-analyzer-service unreachable: {}", e.getMessage());
                    return Mono.just(emptyJob(id));
                });
    }

    // ── Mutations ────────────────────────────────────────────────────────────

    @MutationMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Mono<Map<String, Object>> createJob(@Argument Map<String, Object> input) {
        log.info("GraphQL createJob mutation");
        return gatewayResilience.protect(JOB_BACKEND,
                        webClientBuilder.build()
                                .post().uri(JOB_SERVICE_BASE + "/jobs")
                                .bodyValue(input)
                                .retrieve().bodyToMono(Map.class))
                .map(this::toStringObjectMap)
                .map(this::normalizeJob)
                .onErrorResume(e -> {
                    log.warn("job-analyzer-service unreachable: {}", e.getMessage());
                    return Mono.just(emptyJob("new"));
                });
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Mono<Map<String, Object>> updateJob(@Argument String id, @Argument Map<String, Object> input) {
        log.info("GraphQL updateJob mutation: id={}", id);
        return gatewayResilience.protect(JOB_BACKEND,
                        webClientBuilder.build()
                                .put().uri(JOB_SERVICE_BASE + "/jobs/" + id)
                                .bodyValue(input)
                                .retrieve().bodyToMono(Map.class))
                .map(this::toStringObjectMap)
                .map(this::normalizeJob)
                .onErrorResume(e -> Mono.just(emptyJob(id)));
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Mono<Boolean> deleteJob(@Argument String id) {
        return gatewayResilience.protect(JOB_BACKEND,
                        webClientBuilder.build()
                                .delete().uri(JOB_SERVICE_BASE + "/jobs/" + id)
                                .retrieve().toBodilessEntity())
                .map(ignored -> true)
                .onErrorResume(e -> {
                    log.warn("deleteJob failed: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Mono<Map<String, Object>> publishJob(@Argument String id) {
        return gatewayResilience.protect(JOB_BACKEND,
                        webClientBuilder.build()
                                .post().uri(JOB_SERVICE_BASE + "/jobs/" + id + "/publish")
                                .retrieve().bodyToMono(Map.class))
                .map(this::toStringObjectMap)
                .map(this::normalizeJob)
                .onErrorResume(e -> Mono.just(emptyJob(id)));
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Mono<Map<String, Object>> closeJob(@Argument String id) {
        return gatewayResilience.protect(JOB_BACKEND,
                        webClientBuilder.build()
                                .post().uri(JOB_SERVICE_BASE + "/jobs/" + id + "/close")
                                .retrieve().bodyToMono(Map.class))
                .map(this::toStringObjectMap)
                .map(this::normalizeJob)
                .onErrorResume(e -> Mono.just(emptyJob(id)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Mono<Map<String, Object>> requestMap(String uri) {
        return gatewayResilience.protect(JOB_BACKEND,
                        webClientBuilder.build()
                                .get().uri(uri)
                                .retrieve().bodyToMono(Map.class))
                .map(this::toStringObjectMap);
    }

    private String buildJobsUri(int page, int size, String status, String search, String source) {
        StringBuilder sb = new StringBuilder(JOB_SERVICE_BASE + "/jobs?page=")
                .append(page).append("&size=").append(size);
        if (status != null) sb.append("&status=").append(status);
        if (search != null && !search.isBlank()) sb.append("&search=").append(search);
        if (source != null && !source.isBlank()) sb.append("&source=").append(source);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> emptyPage() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("content", List.of());
        m.put("totalElements", 0);
        m.put("totalPages", 0);
        m.put("page", 0);
        m.put("size", 20);
        return m;
    }

    private Map<String, Object> normalizeJobPage(Map<String, Object> rawPage) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        Object content = rawPage.get("content");
        List<Map<String, Object>> jobs = content instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance).map(item -> normalizeJob((Map<String, Object>) item)).toList()
                : List.of();
        normalized.put("content", jobs);
        normalized.put("totalElements", number(rawPage.get("totalElements"), jobs.size()));
        normalized.put("totalPages", number(rawPage.get("totalPages"), 0));
        normalized.put("page", number(rawPage.get("page"), 0));
        normalized.put("size", number(rawPage.get("size"), 20));
        return normalized;
    }

    private Map<String, Object> emptyJob(String id) {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("id", id);
        j.put("title", "");
        j.put("description", "");
        j.put("department", null);
        j.put("location", null);
        j.put("type", "FULL_TIME");
        j.put("status", "DRAFT");
        j.put("skills", List.of());
        j.put("salaryMin", null);
        j.put("salaryMax", null);
        j.put("salaryCurrency", null);
        j.put("salaryConfidence", null);
        j.put("applicantCount", 0);
        j.put("postedAt", null);
        j.put("closingDate", null);
        return j;
    }

    private Map<String, Object> normalizeJob(Map<String, Object> rawJob) {
        if (rawJob == null || rawJob.isEmpty()) {
            return emptyJob("unknown");
        }
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("id", firstNonNull(rawJob.get("id"), rawJob.get("jobId"), "unknown"));
        j.put("title", firstNonNull(rawJob.get("title"), rawJob.get("jobTitle"), ""));
        j.put("description", firstNonNull(rawJob.get("description"), rawJob.get("jobDescription"), ""));
        j.put("department", rawJob.get("department"));
        j.put("location", rawJob.get("location"));
        j.put("type", firstNonNull(rawJob.get("type"), rawJob.get("employmentType"), "FULL_TIME"));
        j.put("status", firstNonNull(rawJob.get("status"), null, "DRAFT"));
        j.put("skills", list(rawJob.get("skills"), rawJob.get("requiredSkills")));
        j.put("salaryMin", rawJob.get("salaryMin"));
        j.put("salaryMax", rawJob.get("salaryMax"));
        j.put("salaryCurrency", firstNonNull(rawJob.get("salaryCurrency"), rawJob.get("currency"), null));
        j.put("salaryConfidence", firstNonNull(rawJob.get("salaryConfidence"), rawJob.get("confidence"), null));
        j.put("applicantCount", number(rawJob.get("applicantCount"), 0));
        j.put("postedAt", normalizeDateTime(rawJob.get("postedAt")));
        j.put("closingDate", normalizeDateTime(rawJob.get("closingDate")));
        j.put("source", rawJob.get("source"));
        j.put("companyName", firstNonNull(rawJob.get("companyName"), rawJob.get("company"), null));
        j.put("externalUrl", rawJob.get("externalUrl"));
        return j;
    }

    private Object normalizeDateTime(Object value) {
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value);
        if (raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw).toString();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(raw).atOffset(ZoneOffset.UTC).toString();
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private Object firstNonNull(Object a, Object b, Object fallback) {
        return a != null ? a : (b != null ? b : fallback);
    }

    private int number(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> list(Object primary, Object fallback) {
        Object value = primary != null ? primary : fallback;
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toStringObjectMap(Map<?, ?> raw) {
        return raw == null ? emptyPage() : (Map<String, Object>) raw;
    }
}
