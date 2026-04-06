package org.vinod.sha.analyzer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.vinod.sha.analyzer.config.JobSyncProperties;
import org.vinod.sha.analyzer.entity.JobAnalysis;
import org.vinod.sha.analyzer.repository.JobAnalysisRepository;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Periodically (or on-demand) pulls open positions from external job portals
 * and stores them in MongoDB so the candidate portal can display them.
 *
 * Supported sources
 * ─────────────────
 *  JSearch  (RapidAPI) – aggregates LinkedIn, Indeed, Glassdoor, ZipRecruiter
 *  Adzuna              – free REST API, broad coverage
 *
 * Deduplication: each imported job carries an externalId ("SOURCE:id").
 * A job is skipped if that id already exists in the collection.
 */
@Slf4j
@Service
public class JobSyncService {

    // Minimal skill dictionary used for description-based extraction (Adzuna)
    private static final Set<String> SKILL_DICT = Set.of(
            "java", "spring", "spring boot", "react", "angular", "vue", "node.js",
            "python", "sql", "postgresql", "mongodb", "redis", "docker", "kubernetes",
            "aws", "azure", "gcp", "graphql", "grpc", "microservices", "kafka",
            "rabbitmq", "jenkins", "terraform", "typescript", "golang", "rust",
            "llm", "rag", "langchain", "openai", "pytorch", "tensorflow"
    );

    private final JobAnalysisRepository repository;
    private final RestTemplate           restTemplate;
    private final JobSyncProperties      props;

    // ── In-memory sync status (good enough; persist to DB if you want history) ──
    private volatile String        lastSyncStatus = "NEVER_RUN";
    private volatile LocalDateTime lastSyncAt     = null;
    private volatile int           lastImported   = 0;
    private volatile String        lastError      = null;

    public JobSyncService(JobAnalysisRepository repository,
                          RestTemplate restTemplate,
                          JobSyncProperties props) {
        this.repository   = repository;
        this.restTemplate = restTemplate;
        this.props        = props;
    }

    // ── Scheduled entry-point ─────────────────────────────────────────────────

    @Scheduled(cron = "${job-sync.cron:0 0 */6 * * *}")
    public void scheduledSync() {
        if (!props.isEnabled()) {
            log.debug("[JobSync] Sync disabled – skipping scheduled run");
            return;
        }
        log.info("[JobSync] Starting scheduled sync (cron={})", props.getCron());
        triggerSync();
    }

    // ── Public API (manual trigger + status) ─────────────────────────────────

    public Map<String, Object> triggerSync() {
        int total = 0;
        List<String> summary = new ArrayList<>();
        lastError = null;

        try {
            // ── Global (US/default) sources ───────────────────────────────────
            if (hasJSearchKey()) {
                int n = syncFromJSearch();
                total += n;
                summary.add("JSearch(US):+" + n);
            }
            if (hasAdzunaCreds()) {
                int n = syncFromAdzuna();
                total += n;
                summary.add("Adzuna(US):+" + n);
            }

            // ── India sources (Adzuna /in/ + JSearch location=India) ──────────
            if (props.getIndia().isEnabled()) {
                if (hasJSearchKey()) {
                    int n = syncFromJSearchIndia();
                    total += n;
                    summary.add("JSearch(IN):+" + n);
                }
                if (hasAdzunaCreds()) {
                    int n = syncFromAdzunaIndia();
                    total += n;
                    summary.add("Adzuna(IN):+" + n);
                }
            }

            if (!hasJSearchKey() && !hasAdzunaCreds()) {
                summary.add("No API credentials configured – set JSEARCH_API_KEY / ADZUNA_APP_ID / ADZUNA_APP_KEY");
            }
            lastSyncStatus = "OK";
        } catch (Exception e) {
            lastSyncStatus = "ERROR";
            lastError = e.getMessage();
            log.error("[JobSync] Sync failed: {}", e.getMessage(), e);
        }

        lastSyncAt   = LocalDateTime.now();
        lastImported = total;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status",        lastSyncStatus);
        result.put("importedCount", total);
        result.put("sources",       summary);
        result.put("syncedAt",      lastSyncAt.toString());
        if (lastError != null) result.put("error", lastError);
        return result;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("status",           lastSyncStatus);
        s.put("lastSyncAt",       lastSyncAt != null ? lastSyncAt.toString() : null);
        s.put("lastImportedCount",lastImported);
        s.put("enabled",          props.isEnabled());
        s.put("indiaEnabled",     props.getIndia().isEnabled());
        s.put("configuredSources", buildSourcesInfo());
        if (lastError != null) s.put("lastError", lastError);
        return s;
    }

    // ── JSearch (RapidAPI) ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private int syncFromJSearch() {
        int imported = 0;
        String apiKey  = props.getJsearch().getApiKey();
        String baseUrl = props.getJsearch().getBaseUrl();

        for (String keyword : props.getKeywords()) {
            if (imported >= props.getMaxPerSync()) break;
            try {
                String url = baseUrl + "/search"
                        + "?query="       + encode(keyword)
                        + "&page=1"
                        + "&num_pages="   + props.getJsearch().getNumPages()
                        + "&date_posted=" + props.getJsearch().getDatePosted()
                        + "&country="     + props.getCountry();

                HttpHeaders headers = new HttpHeaders();
                headers.set("X-RapidAPI-Key",  apiKey);
                headers.set("X-RapidAPI-Host", "jsearch.p.rapidapi.com");

                ResponseEntity<Map> response = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

                if (response.getBody() == null) continue;
                List<Map<String, Object>> data =
                        (List<Map<String, Object>>) response.getBody().get("data");
                if (data == null) continue;

                for (Map<String, Object> job : data) {
                    if (imported >= props.getMaxPerSync()) break;
                    if (saveJSearchJob(job)) imported++;
                }

            } catch (Exception e) {
                log.warn("[JobSync][JSearch] keyword='{}' error: {}", keyword, e.getMessage());
            }
        }
        log.info("[JobSync][JSearch] Imported {} new jobs", imported);
        return imported;
    }

    @SuppressWarnings("unchecked")
    private boolean saveJSearchJob(Map<String, Object> raw) {
        return saveJSearchJobTagged(raw, "JSEARCH");
    }

    @SuppressWarnings("unchecked")
    private boolean saveJSearchJobTagged(Map<String, Object> raw, String sourceTag) {
        String jobId = str(raw.get("job_id"));
        if (blank(jobId)) return false;

        String externalId = sourceTag + ":" + jobId;
        if (repository.existsByExternalId(externalId)) return false;

        String description = str(raw.get("job_description"));
        String city        = str(raw.get("job_city"));
        String state       = str(raw.get("job_state"));
        boolean remote     = Boolean.TRUE.equals(raw.get("job_is_remote"));

        // Skills: use explicit list from API first, fall back to dict extraction
        List<Object> rawSkills = raw.get("job_required_skills") instanceof List
                ? (List<Object>) raw.get("job_required_skills") : List.of();
        List<String> skills = rawSkills.isEmpty()
                ? extractFromDescription(description)
                : rawSkills.stream().map(Object::toString).toList();

        repository.save(JobAnalysis.builder()
                .jobId(UUID.randomUUID().toString())
                .externalId(externalId)
                .source(sourceTag)
                .externalUrl(str(raw.get("job_apply_link")))
                .jobTitle(str(raw.get("job_title")))
                .companyName(str(raw.get("employer_name")))
                .companyLogo(str(raw.get("employer_logo")))
                .location(buildLocation(city, state, remote))
                .employmentType(mapJSearchType(str(raw.get("job_employment_type"))))
                .jobDescription(description)
                .requiredSkills(skills)
                .preferredSkills(List.of())
                .salaryMin(toInt(raw.get("job_min_salary")))
                .salaryMax(toInt(raw.get("job_max_salary")))
                .currency(firstNonBlank(str(raw.get("job_salary_currency")), "USD"))
                .status("OPEN")
                .applicantCount(0)
                .postedAt(parseIso(str(raw.get("job_posted_at_datetime_utc"))))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        return true;
    }

    // ── Adzuna ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private int syncFromAdzuna() {
        int imported = 0;
        String appId   = props.getAdzuna().getAppId();
        String appKey  = props.getAdzuna().getAppKey();
        String baseUrl = props.getAdzuna().getBaseUrl();

        for (String keyword : props.getKeywords()) {
            if (imported >= props.getMaxPerSync()) break;
            try {
                String url = baseUrl + "/jobs/" + props.getCountry() + "/search/1"
                        + "?app_id="          + appId
                        + "&app_key="         + appKey
                        + "&results_per_page="+ props.getAdzuna().getResultsPerPage()
                        + "&what="            + encode(keyword)
                        + "&content-type=application/json";

                Map<String, Object> response = restTemplate.getForObject(url, Map.class);
                if (response == null) continue;

                List<Map<String, Object>> results =
                        (List<Map<String, Object>>) response.get("results");
                if (results == null) continue;

                for (Map<String, Object> job : results) {
                    if (imported >= props.getMaxPerSync()) break;
                    if (saveAdzunaJob(job)) imported++;
                }

            } catch (Exception e) {
                log.warn("[JobSync][Adzuna] keyword='{}' error: {}", keyword, e.getMessage());
            }
        }
        log.info("[JobSync][Adzuna] Imported {} new jobs", imported);
        return imported;
    }

    @SuppressWarnings("unchecked")
    private boolean saveAdzunaJob(Map<String, Object> raw) {
        return saveAdzunaJobTagged(raw, "ADZUNA");
    }

    @SuppressWarnings("unchecked")
    private boolean saveAdzunaJobTagged(Map<String, Object> raw, String sourceTag) {
        String jobId = str(raw.get("id"));
        if (blank(jobId)) return false;

        String externalId = sourceTag + ":" + jobId;
        if (repository.existsByExternalId(externalId)) return false;

        String description = str(raw.get("description"));

        // Adzuna nested "company" and "location" objects
        String companyName = null;
        if (raw.get("company") instanceof Map<?,?> co)
            companyName = str(co.get("display_name"));
        String location = null;
        if (raw.get("location") instanceof Map<?,?> loc)
            location = str(loc.get("display_name"));

        repository.save(JobAnalysis.builder()
                .jobId(UUID.randomUUID().toString())
                .externalId(externalId)
                .source(sourceTag)
                .externalUrl(str(raw.get("redirect_url")))
                .jobTitle(str(raw.get("title")))
                .companyName(companyName)
                .location(location)
                .employmentType(mapAdzunaType(str(raw.get("contract_time"))))
                .jobDescription(description)
                .requiredSkills(extractFromDescription(description))
                .preferredSkills(List.of())
                .salaryMin(toInt(raw.get("salary_min")))
                .salaryMax(toInt(raw.get("salary_max")))
                .currency("INR")
                .status("OPEN")
                .applicantCount(0)
                .postedAt(parseIso(str(raw.get("created"))))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        return true;
    }

    // ── JSearch India ─────────────────────────────────────────────────────────
    // Reuses the same RapidAPI key, adds location=India filter.
    // Returns LinkedIn India + Indeed.co.in results including PSU / govt employers.

    @SuppressWarnings("unchecked")
    private int syncFromJSearchIndia() {
        int imported = 0;
        String apiKey  = props.getJsearch().getApiKey();
        String baseUrl = props.getJsearch().getBaseUrl();
        int    limit   = props.getIndia().getMaxPerSync();

        for (String keyword : props.getIndia().getKeywords()) {
            if (imported >= limit) break;
            try {
                String url = baseUrl + "/search"
                        + "?query="       + encode(keyword)
                        + "&page=1"
                        + "&num_pages=1"
                        + "&date_posted=" + props.getJsearch().getDatePosted()
                        + "&country=in";

                HttpHeaders headers = new HttpHeaders();
                headers.set("X-RapidAPI-Key",  apiKey);
                headers.set("X-RapidAPI-Host", "jsearch.p.rapidapi.com");

                ResponseEntity<Map> response = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

                if (response.getBody() == null) continue;
                List<Map<String, Object>> data =
                        (List<Map<String, Object>>) response.getBody().get("data");
                if (data == null) continue;

                for (Map<String, Object> job : data) {
                    if (imported >= limit) break;
                    // Tag as JSEARCH_IN so status endpoint differentiates from US results
                    if (saveJSearchJobTagged(job, "JSEARCH_IN")) imported++;
                }
            } catch (Exception e) {
                log.warn("[JobSync][JSearch-IN] keyword='{}' error: {}", keyword, e.getMessage());
            }
        }
        log.info("[JobSync][JSearch-IN] Imported {} new India jobs", imported);
        return imported;
    }

    // ── Adzuna India ──────────────────────────────────────────────────────────
    // Adzuna /in/ aggregates Naukri, LinkedIn India, Indeed.co.in, TimesJobs.
    // Government / PSU employers appear when filtered by relevant keywords.

    @SuppressWarnings("unchecked")
    private int syncFromAdzunaIndia() {
        int imported = 0;
        String appId   = props.getAdzuna().getAppId();
        String appKey  = props.getAdzuna().getAppKey();
        String baseUrl = props.getAdzuna().getBaseUrl();
        int    limit   = props.getIndia().getMaxPerSync();

        for (String keyword : props.getIndia().getKeywords()) {
            if (imported >= limit) break;
            try {
                String url = baseUrl + "/jobs/in/search/1"
                        + "?app_id="          + appId
                        + "&app_key="         + appKey
                        + "&results_per_page="+ props.getAdzuna().getResultsPerPage()
                        + "&what="            + encode(keyword)
                        + "&content-type=application/json";

                Map<String, Object> response = restTemplate.getForObject(url, Map.class);
                if (response == null) continue;

                List<Map<String, Object>> results =
                        (List<Map<String, Object>>) response.get("results");
                if (results == null) continue;

                for (Map<String, Object> job : results) {
                    if (imported >= limit) break;
                    if (saveAdzunaJobTagged(job, "ADZUNA_IN")) imported++;
                }
            } catch (Exception e) {
                log.warn("[JobSync][Adzuna-IN] keyword='{}' error: {}", keyword, e.getMessage());
            }
        }
        log.info("[JobSync][Adzuna-IN] Imported {} new India jobs", imported);
        return imported;
    }

    // ── Skill extraction (dictionary-based) ───────────────────────────────────

    private List<String> extractFromDescription(String text) {
        if (text == null || text.isBlank()) return List.of();
        String lower = text.toLowerCase();
        Set<String> found = new LinkedHashSet<>();
        for (String skill : SKILL_DICT) {
            if (lower.contains(skill)) found.add(capitalize(skill));
        }
        return new ArrayList<>(found);
    }

    // ── Employment-type mapping ───────────────────────────────────────────────

    private String mapJSearchType(String type) {
        if (type == null) return "FULL_TIME";
        return switch (type.toUpperCase()) {
            case "FULLTIME"   -> "FULL_TIME";
            case "PARTTIME"   -> "PART_TIME";
            case "CONTRACTOR" -> "CONTRACT";
            case "INTERN"     -> "INTERN";
            default           -> "FULL_TIME";
        };
    }

    private String mapAdzunaType(String contractTime) {
        if (contractTime == null) return "FULL_TIME";
        return switch (contractTime.toLowerCase()) {
            case "full_time"  -> "FULL_TIME";
            case "part_time"  -> "PART_TIME";
            case "contract"   -> "CONTRACT";
            default           -> "FULL_TIME";
        };
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private String buildLocation(String city, String state, boolean remote) {
        if (remote) return "Remote";
        if (blank(city) && blank(state)) return null;
        if (blank(state)) return city;
        if (blank(city))  return state;
        return city + ", " + state;
    }

    private LocalDateTime parseIso(String raw) {
        if (raw == null || raw.isBlank()) return LocalDateTime.now();
        try { return OffsetDateTime.parse(raw).toLocalDateTime(); }
        catch (DateTimeParseException ignored) { return LocalDateTime.now(); }
    }

    private String str(Object o)   { return o == null ? null : o.toString().trim(); }
    private boolean blank(String s){ return s == null || s.isBlank(); }

    private String firstNonBlank(String a, String b) { return !blank(a) ? a : b; }

    private Integer toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return null;
        try { return (int) Math.round(Double.parseDouble(v.toString())); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private boolean hasJSearchKey()   { return !blank(props.getJsearch().getApiKey()); }
    private boolean hasAdzunaCreds()  {
        return !blank(props.getAdzuna().getAppId()) && !blank(props.getAdzuna().getAppKey());
    }

    private List<String> buildSourcesInfo() {
        List<String> info = new ArrayList<>();
        info.add("JSearch (RapidAPI) US : " + (hasJSearchKey()  ? "CONFIGURED" : "NOT CONFIGURED – set JSEARCH_API_KEY"));
        info.add("Adzuna          US   : " + (hasAdzunaCreds() ? "CONFIGURED" : "NOT CONFIGURED – set ADZUNA_APP_ID + ADZUNA_APP_KEY"));
        info.add("India sync           : " + (props.getIndia().isEnabled() ? "ENABLED (Adzuna/in + JSearch country=in)" : "DISABLED – set JOB_SYNC_INDIA_ENABLED=true"));
        return info;
    }
}

