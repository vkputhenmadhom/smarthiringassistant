package org.vinod.sha.gateway.graphql;

import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * GraphQL resolver that acts as a BFF (Backend For Frontend) aggregating
 * calls to the individual microservices via their REST APIs.
 */
@Slf4j
@Controller
public class JobGraphQLResolver {

    private final WebClient.Builder webClientBuilder;

    public JobGraphQLResolver(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    @QueryMapping
    public Map<String, Object> jobs(
            @Argument Integer page,
            @Argument Integer size,
            @Argument String status,
            @Argument String search) {
        log.debug("GraphQL jobs query: page={}, size={}, status={}, search={}", page, size, status, search);
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;

        String uri = buildJobsUri(p, s, status, search);
        try {
            Map<?, ?> result = webClientBuilder.build()
                    .get().uri(uri)
                    .retrieve().bodyToMono(Map.class).block();
            return result != null ? (Map<String, Object>) result : emptyPage();
        } catch (Exception e) {
            log.warn("job-analyzer-service unreachable, returning stub: {}", e.getMessage());
            return stubJobPage();
        }
    }

    @QueryMapping
    public Map<String, Object> job(@Argument String id) {
        log.debug("GraphQL job query: id={}", id);
        try {
            return webClientBuilder.build()
                    .get().uri("http://job-analyzer-service:8005/jobs/" + id)
                    .retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            log.warn("job-analyzer-service unreachable: {}", e.getMessage());
            return stubJob(id);
        }
    }

    // ── Mutations ────────────────────────────────────────────────────────────

    @MutationMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Map<String, Object> createJob(@Argument Map<String, Object> input) {
        log.info("GraphQL createJob mutation");
        try {
            return webClientBuilder.build()
                    .post().uri("http://job-analyzer-service:8005/jobs")
                    .bodyValue(input)
                    .retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            log.warn("job-analyzer-service unreachable: {}", e.getMessage());
            return stubJob("new");
        }
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Map<String, Object> updateJob(@Argument String id, @Argument Map<String, Object> input) {
        log.info("GraphQL updateJob mutation: id={}", id);
        try {
            return webClientBuilder.build()
                    .put().uri("http://job-analyzer-service:8005/jobs/" + id)
                    .bodyValue(input)
                    .retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            return stubJob(id);
        }
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Boolean deleteJob(@Argument String id) {
        try {
            webClientBuilder.build()
                    .delete().uri("http://job-analyzer-service:8005/jobs/" + id)
                    .retrieve().toBodilessEntity().block();
            return true;
        } catch (Exception e) {
            log.warn("deleteJob failed: {}", e.getMessage());
            return false;
        }
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Map<String, Object> publishJob(@Argument String id) {
        try {
            return webClientBuilder.build()
                    .post().uri("http://job-analyzer-service:8005/jobs/" + id + "/publish")
                    .retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            return stubJob(id);
        }
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','RECRUITER')")
    public Map<String, Object> closeJob(@Argument String id) {
        try {
            return webClientBuilder.build()
                    .post().uri("http://job-analyzer-service:8005/jobs/" + id + "/close")
                    .retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            return stubJob(id);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildJobsUri(int page, int size, String status, String search) {
        StringBuilder sb = new StringBuilder("http://job-analyzer-service:8005/jobs?page=")
                .append(page).append("&size=").append(size);
        if (status != null) sb.append("&status=").append(status);
        if (search != null && !search.isBlank()) sb.append("&search=").append(search);
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

    private Map<String, Object> stubJobPage() {
        Map<String, Object> m = emptyPage();
        m.put("content", List.of(stubJob("1"), stubJob("2")));
        m.put("totalElements", 2);
        m.put("totalPages", 1);
        return m;
    }

    private Map<String, Object> stubJob(String id) {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("id", id);
        j.put("title", "Sample Senior Engineer");
        j.put("description", "Placeholder job — service unavailable");
        j.put("department", "Engineering");
        j.put("location", "Remote");
        j.put("type", "FULL_TIME");
        j.put("status", "OPEN");
        j.put("skills", List.of("Java", "Spring Boot", "Kubernetes"));
        j.put("salaryMin", 120000);
        j.put("salaryMax", 160000);
        j.put("salaryCurrency", "USD");
        j.put("salaryConfidence", 0.85);
        j.put("applicantCount", 0);
        return j;
    }
}

