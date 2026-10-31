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
import org.vinod.sha.analyzer.config.JobSyncProperties.Feed;
import org.vinod.sha.analyzer.config.JobSyncProperties.FeedSource;
import org.vinod.sha.analyzer.entity.JobAnalysis;
import org.vinod.sha.analyzer.repository.JobAnalysisRepository;
import org.vinod.shared.text.SkillText;

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
import java.util.concurrent.atomic.AtomicBoolean;

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
 * 
 * Java 21 Modernizations:
 * - Virtual threads for parallel job syncing from multiple sources
 * - Text blocks for multi-line URL formatting
 * - Enhanced switch expressions for employment type mapping
 * - Pattern matching with instanceof for type conversions
 * - Stream.toList() for immutable collection results
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
    private final JobFeeds               jobFeeds;

    // ── In-memory sync status (good enough; persist to DB if you want history) ──
    private volatile String        lastSyncStatus = "NEVER_RUN";
    private volatile LocalDateTime lastSyncAt     = null;
    private volatile int           lastImported   = 0;
    private volatile String        lastError      = null;

    // ── Java 21 Virtual Thread support ─────────────────────────────────────────
    private final AtomicBoolean    syncInProgress = new AtomicBoolean(false);
    private volatile String        currentSyncId  = null;

    public JobSyncService(JobAnalysisRepository repository,
                          RestTemplate restTemplate,
                          JobSyncProperties props,
                          JobFeeds jobFeeds) {
        this.repository   = repository;
        this.restTemplate = restTemplate;
        this.props        = props;
        this.jobFeeds     = jobFeeds;
    }

    // ── Scheduled entry-point ─────────────────────────────────────────────────

    @Scheduled(cron = "${job-sync.cron:0 30 0 * * *}")
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
        var summary = new ArrayList<String>();
        lastError = null;

        try {
            // Java 21: Use virtual threads for parallel job syncing
            var threads = new ArrayList<Thread>();
            var results = new java.util.concurrent.ConcurrentHashMap<String, Integer>();
            
            // ── Global (US/default) sources ───────────────────────────────────
            if (props.isGlobalEnabled() && hasJSearchKey()) {
                threads.add(Thread.startVirtualThread(() -> 
                    results.put("jsearch", syncFromJSearch())
                ));
            }
            if (props.isGlobalEnabled() && hasAdzunaCreds()) {
                threads.add(Thread.startVirtualThread(() -> 
                    results.put("adzuna", syncFromAdzuna())
                ));
            }

            // ── Configured feeds (job-feeds.yml): one thread per provider, feeds in config order ──
            if (props.isFeedsEnabled()) {
                if (hasJSearchKey()) {
                    threads.add(Thread.startVirtualThread(() -> syncFeeds(JSEARCH, results)));
                }
                if (hasAdzunaCreds()) {
                    threads.add(Thread.startVirtualThread(() -> syncFeeds(ADZUNA, results)));
                }
            }

            // Wait for all virtual threads to complete
            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Sync thread interrupted: {}", e.getMessage());
                }
            }
            
            // Collect results and build summary
            var total = results.values().stream().mapToInt(Integer::intValue).sum();
            results.forEach((source, count) -> {
                summary.add(switch(source) {
                    case "jsearch" -> "JSearch(US):+" + count;
                    case "adzuna" -> "Adzuna(US):+" + count;
                    default -> source + ":+" + count;
                });
            });

            if (!hasJSearchKey() && !hasAdzunaCreds()) {
                summary.add("No API credentials configured – set JSEARCH_API_KEY / ADZUNA_APP_ID / ADZUNA_APP_KEY");
            }
            lastSyncStatus = "OK";
            lastImported = total;
        } catch (Exception e) {
            lastSyncStatus = "ERROR";
            lastError = e.getMessage();
            log.error("[JobSync] Sync failed: {}", e.getMessage(), e);
            lastImported = 0;
        }

        lastSyncAt = LocalDateTime.now();

        var result = new LinkedHashMap<String, Object>();
        result.put("status",        lastSyncStatus);
        result.put("importedCount", lastImported);
        result.put("sources",       summary);
        result.put("syncedAt",      lastSyncAt.toString());
        if (lastError != null) result.put("error", lastError);
        return result;
    }

    /**
     * Java 21 Virtual Thread: Trigger sync asynchronously.
     * Returns immediately with a sync ID; actual work happens in the background.
     * 
     * Uses Thread.startVirtualThread() - efficient lightweight threads from Project Loom.
     */
    public String triggerSyncAsync() {
        // compareAndSet so two concurrent triggers cannot both start a sync
        if (!syncInProgress.compareAndSet(false, true)) {
           log.warn("[JobSync] Sync already in progress ({})", currentSyncId);
           return currentSyncId;
        }

        String syncId = UUID.randomUUID().toString();
        currentSyncId = syncId;
        lastError = null;

        Thread.startVirtualThread(() -> {
           try {
               log.info("[JobSync][VirtualThread-{}] Starting async sync", syncId);
               triggerSync();
           } catch (Exception e) {
               log.error("[JobSync][VirtualThread-{}] Sync failed: {}", syncId, e.getMessage(), e);
               lastError = e.getMessage();
           } finally {
               syncInProgress.set(false);
               log.info("[JobSync][VirtualThread-{}] Sync completed", syncId);
           }
        });

        return syncId;
    }

    public Map<String, Object> getStatus() {
        var s = new LinkedHashMap<String, Object>();
        s.put("status",           lastSyncStatus);
        s.put("lastSyncAt",       lastSyncAt != null ? lastSyncAt.toString() : null);
        s.put("lastImportedCount",lastImported);
        s.put("syncInProgress",   syncInProgress.get());
        s.put("currentSyncId",    currentSyncId);
        s.put("enabled",          props.isEnabled());
        s.put("globalEnabled",    props.isGlobalEnabled());
        s.put("feedsEnabled",     props.isFeedsEnabled());
        s.put("configuredSources", buildSourcesInfo());
        if (lastError != null) s.put("lastError", lastError);
        return s;
    }

    // ── JSearch (RapidAPI) ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private int syncFromJSearch() {
        var imported = 0;
        var apiKey  = props.getJsearch().getApiKey();
        var baseUrl = props.getJsearch().getBaseUrl();

        for (var keyword : props.getKeywords()) {
            if (imported >= props.getMaxPerSync()) break;
            try {
                var url = """
                    %s/search?query=%s&page=1&num_pages=%d&date_posted=%s&country=%s
                    """.formatted(
                        baseUrl,
                        encode(keyword),
                        props.getJsearch().getNumPages(),
                        props.getJsearch().getDatePosted(),
                        props.getCountry()
                    ).replaceAll("\\s+", "");

                var headers = new HttpHeaders();
                headers.set("X-RapidAPI-Key",  apiKey);
                headers.set("X-RapidAPI-Host", "jsearch.p.rapidapi.com");

                var response = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

                if (response.getBody() == null) continue;
                var data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data == null) continue;

                for (var job : data) {
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
        return saveJSearchJobTagged(raw, new Tag("JSEARCH", "JSEARCH"));
    }

    /** @param tag source tag to store, and the external-id prefix shared by feeds that must not import the same job twice */
    @SuppressWarnings("unchecked")
    private boolean saveJSearchJobTagged(Map<String, Object> raw, Tag tag) {
        String jobId = str(raw.get("job_id"));
        if (blank(jobId)) return false;

        String externalId = tag.externalIdPrefix() + ":" + jobId;
        String sourceTag = tag.sourceTag();
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
        return saveAdzunaJobTagged(raw, new Tag("ADZUNA", "ADZUNA"));
    }

    @SuppressWarnings("unchecked")
    private boolean saveAdzunaJobTagged(Map<String, Object> raw, Tag tag) {
        String jobId = str(raw.get("id"));
        if (blank(jobId)) return false;

        String externalId = tag.externalIdPrefix() + ":" + jobId;
        String sourceTag = tag.sourceTag();
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

    // ── Configured feeds (job-feeds.yml) ───────────────────────────────────────

    private static final String JSEARCH = "jsearch";
    private static final String ADZUNA  = "adzuna";

    record Tag(String sourceTag, String externalIdPrefix) {}

    /** Runs every configured feed's sources on one provider, in config order; adds "provider:feed" -> new jobs. */
    private void syncFeeds(String provider, Map<String, Integer> results) {
        for (Feed feed : jobFeeds.all()) {
            List<FeedSource> sources = feed.getSources() == null ? List.of() : feed.getSources().stream()
                    .filter(src -> provider.equalsIgnoreCase(src.getProvider()))
                    .toList();
            if (feed.isPostedInApp() || sources.isEmpty()) continue;

            Tag tag = new Tag(JobFeeds.sourceTag(provider, feed), JobFeeds.externalIdPrefix(provider, feed));
            int imported = 0;
            int rejected = 0;
            searches:
            for (FeedSource source : sources) {
                for (String keyword : source.getKeywords()) {
                    if (imported >= feed.getMaxPerSync()) break searches;
                    for (Map<String, Object> job : search(provider, source, keyword)) {
                        if (imported >= feed.getMaxPerSync()) break searches;
                        String title = str(job.get(JSEARCH.equals(provider) ? "job_title" : "title"));
                        String description = str(job.get(JSEARCH.equals(provider) ? "job_description" : "description"));
                        if (!jobFeeds.accepts(feed, title, description)) {
                            rejected++;
                            continue;
                        }
                        boolean saved = JSEARCH.equals(provider) ? saveJSearchJobTagged(job, tag) : saveAdzunaJobTagged(job, tag);
                        if (saved) imported++;
                    }
                }
            }
            log.info("[JobSync][{}] Feed '{}': imported {} new jobs, {} left out by its rules", provider, feed.getKey(), imported, rejected);
            results.put(provider + ":" + feed.getKey(), imported);
        }
    }

    /** One API request; an error is logged and yields no jobs. */
    private List<Map<String, Object>> search(String provider, FeedSource source, String keyword) {
        try {
            return JSEARCH.equals(provider) ? jsearch(source, keyword) : adzuna(source, keyword);
        } catch (Exception e) {
            log.warn("[JobSync][{}] keyword='{}' error: {}", provider, keyword, e.getMessage());
            return List.of();
        }
    }

    /** JSearch (LinkedIn, Indeed, Glassdoor, …); counts against its monthly request quota. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> jsearch(FeedSource source, String keyword) {
        String url = props.getJsearch().getBaseUrl() + "/search"
                + "?query="       + encode(keyword)
                + "&page=1"
                + "&num_pages=1"
                + "&date_posted=" + props.getJsearch().getDatePosted()
                + (blank(source.getCountry()) ? "" : "&country=" + encode(source.getCountry()))
                + extraParams(source);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RapidAPI-Key",  props.getJsearch().getApiKey());
        headers.set("X-RapidAPI-Host", "jsearch.p.rapidapi.com");

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        if (response.getBody() == null) return List.of();
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        return data == null ? List.of() : data;
    }

    /** Adzuna for one country (India: Naukri, LinkedIn India, Indeed.co.in, TimesJobs). */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> adzuna(FeedSource source, String keyword) {
        if (blank(source.getCountry())) {
            log.warn("[JobSync][adzuna] keyword='{}' skipped: an Adzuna source needs a country", keyword);
            return List.of();
        }
        String url = props.getAdzuna().getBaseUrl() + "/jobs/" + encode(source.getCountry().toLowerCase()) + "/search/1"
                + "?app_id="          + props.getAdzuna().getAppId()
                + "&app_key="         + props.getAdzuna().getAppKey()
                + "&results_per_page="+ props.getAdzuna().getResultsPerPage()
                + "&what="            + encode(keyword)
                + extraParams(source)
                + "&content-type=application/json";

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null) return List.of();
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
        return results == null ? List.of() : results;
    }

    private String extraParams(FeedSource source) {
        if (source.getParams() == null) return "";
        StringBuilder sb = new StringBuilder();
        source.getParams().forEach((name, value) -> sb.append('&').append(encode(name)).append('=').append(encode(value)));
        return sb.toString();
    }

    // ── Skill extraction (dictionary-based) ───────────────────────────────────

    private List<String> extractFromDescription(String text) {
        if (text == null || text.isBlank()) return List.of();
        Set<String> found = new LinkedHashSet<>();
        for (String skill : SkillText.find(text, SKILL_DICT)) {
            found.add(capitalize(skill));
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
        // JSearch sometimes sends the text "undefined" for a missing city or state
        if ("undefined".equalsIgnoreCase(city))  city = null;
        if ("undefined".equalsIgnoreCase(state)) state = null;
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
        // Java 21: Pattern matching with instanceof
        if (v instanceof Number n) return n.intValue();
        if (v == null) return null;
        try { 
            return (int) Math.round(Double.parseDouble(v.toString())); 
        } catch (NumberFormatException ignored) { 
            return null; 
        }
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
        info.add("US/global sync       : " + (props.isGlobalEnabled() ? "ENABLED" : "PAUSED – set JOB_SYNC_GLOBAL_ENABLED=true"));
        info.add("JSearch (RapidAPI) US : " + (hasJSearchKey()  ? "CONFIGURED" : "NOT CONFIGURED – set JSEARCH_API_KEY"));
        info.add("Adzuna          US   : " + (hasAdzunaCreds() ? "CONFIGURED" : "NOT CONFIGURED – set ADZUNA_APP_ID + ADZUNA_APP_KEY"));
        info.add("Feeds (job-feeds.yml): " + (props.isFeedsEnabled()
                ? "ENABLED – " + jobFeeds.all().stream().map(Feed::getKey).toList()
                : "DISABLED – set JOB_SYNC_INDIA_ENABLED=true"));
        return info;
    }
}

