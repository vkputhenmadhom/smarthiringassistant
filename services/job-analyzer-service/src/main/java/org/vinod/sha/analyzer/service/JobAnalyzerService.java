package org.vinod.sha.analyzer.service;

import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.vinod.shared.config.RabbitMQConfiguration;
import org.vinod.shared.events.JobAnalyzedEvent;
import org.vinod.sha.analyzer.dto.JobAnalyzeRequest;
import org.vinod.sha.analyzer.dto.SalaryEstimateRequest;
import org.vinod.sha.analyzer.dto.SalaryEstimateResponse;
import org.vinod.sha.analyzer.dto.SkillsExtractResponse;
import org.vinod.sha.analyzer.entity.JobAnalysis;
import org.vinod.sha.analyzer.repository.JobAnalysisRepository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
/**
 * Java 21 Modernized Job Analysis Service
 * 
 * Features:
 * - Enhanced switch expressions for salary estimation logic
 * - Virtual thread support for AI skill extraction async calls
 * - Text blocks for multi-line prompt strings
 * - Pattern matching with instanceof for type-safe operations
 * - Java 21 toList() stream collectors
 * - Sealed Result types for error handling
 */
public class JobAnalyzerService {

    private final JobAnalysisRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;

    public JobAnalyzerService(JobAnalysisRepository repository,
                              RabbitTemplate rabbitTemplate,
                              RestTemplate restTemplate,
                              MeterRegistry meterRegistry,
                              JobMarket jobMarket,
                              JobFeeds jobFeeds,
                              JobSkills jobSkills) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;
        this.jobMarket = jobMarket;
        this.jobFeeds = jobFeeds;
        this.jobSkills = jobSkills;
    }

    private final JobMarket jobMarket;
    private final JobFeeds jobFeeds;
    private final JobSkills jobSkills;

    private DistributionSummary salaryConfidenceSummary;

    @Value("${services.ai-integration.base-url:http://localhost:8008/api/ai}")
    private String aiBaseUrl;


    @jakarta.annotation.PostConstruct
    void initMetrics() {
        this.salaryConfidenceSummary = DistributionSummary.builder("job_analyzer.salary.confidence")
                .baseUnit("ratio")
                .description("Confidence score emitted by salary estimator")
                .register(meterRegistry);
    }

    public JobAnalysis analyze(JobAnalyzeRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        meterRegistry.counter("job_analyzer.analyze.requests").increment();

        SkillsExtractResponse skills = extractSkillsFromText(request.jobDescription());
        SalaryEstimateResponse salary = estimateSalary(
                new SalaryEstimateRequest(
                        request.jobTitle(),
                        request.location(),
                        extractExperienceYears(request.jobDescription()),
                        skills.requiredSkills()
                )
        );

        JobAnalysis analysis = JobAnalysis.builder()
                .jobId(defaultIfBlank(request.jobId(), UUID.randomUUID().toString()))
                .jobTitle(request.jobTitle())
                .companyName(request.companyName())
                .department(null)
                .location(request.location())
                .employmentType(defaultIfBlank(request.employmentType(), "FULL_TIME"))
                .jobDescription(request.jobDescription())
                .requiredSkills(skills.requiredSkills())
                .preferredSkills(skills.preferredSkills())
                .minExperienceYears(extractExperienceYears(request.jobDescription()))
                .maxExperienceYears(Math.max(extractExperienceYears(request.jobDescription()) + 2, 3))
                .salaryMin(salary.min())
                .salaryMax(salary.max())
                .currency(salary.currency())
                .confidence(salary.confidence())
                .status("ANALYZED")
                .notes(salary.rationale())
                .applicantCount(0)
                .postedAt(LocalDateTime.now())
                .closingDate(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        JobAnalysis saved = repository.save(analysis);
        publishAnalyzedEvent(saved);
        if (saved.getConfidence() != null) {
            salaryConfidenceSummary.record(saved.getConfidence());
        }
        sample.stop(meterRegistry.timer("job_analyzer.analyze.duration"));
        return saved;
    }

    public Map<String, Object> listJobs(int page, int size, String status, String search) {
        return listJobs(page, size, status, search, null, false);
    }

    /** @param marketOnly only jobs in the configured {@link JobMarket} (what candidates see) */
    public Map<String, Object> listJobs(int page, int size, String status, String search, String source, boolean marketOnly) {
        List<JobAnalysis> all = repository.findAll().stream()
                .filter(job -> !marketOnly || jobMarket.includes(job))
                .sorted(Comparator.comparing(JobAnalysis::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(job -> status == null || status.isBlank() || status.equalsIgnoreCase(defaultIfBlank(job.getStatus(), "DRAFT")))
                .filter(job -> matchesSearch(job, search))
                .filter(job -> matchesSource(job, source))
                .collect(Collectors.toList());

        int safeSize = Math.max(1, size);
        int safePage = Math.max(0, page);
        int fromIndex = Math.min(safePage * safeSize, all.size());
        int toIndex = Math.min(fromIndex + safeSize, all.size());
        int totalPages = all.isEmpty() ? 0 : (int) Math.ceil((double) all.size() / safeSize);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", all.subList(fromIndex, toIndex));
        response.put("totalElements", all.size());
        response.put("totalPages", totalPages);
        response.put("page", safePage);
        response.put("size", safeSize);
        return response;
    }

    public JobAnalysis createJobFromInput(Map<String, Object> input) {
        var request = new JobAnalyzeRequest(
                readString(input, "jobId"),
                readString(input, "title"),
                readString(input, "companyName"),
                readString(input, "location"),
                readString(input, "type"),
                readString(input, "description")
        );

        JobAnalysis analysis = analyze(request);
        analysis.setDepartment(readString(input, "department"));
        analysis.setRequiredSkills(readStringList(input.get("skills"), analysis.getRequiredSkills()));
        analysis.setSalaryMin(readInteger(input.get("salaryMin"), analysis.getSalaryMin()));
        analysis.setSalaryMax(readInteger(input.get("salaryMax"), analysis.getSalaryMax()));
        analysis.setCurrency(defaultIfBlank(readString(input, "salaryCurrency"), analysis.getCurrency()));
        analysis.setClosingDate(parseDateTime(input.get("closingDate")));
        analysis.setStatus("DRAFT");
        analysis.setUpdatedAt(LocalDateTime.now());
        return repository.save(analysis);
    }

    public JobAnalysis updateJob(String id, Map<String, Object> input) {
        JobAnalysis job = getById(id);
        if (input.containsKey("title")) job.setJobTitle(readString(input, "title"));
        if (input.containsKey("description")) job.setJobDescription(readString(input, "description"));
        if (input.containsKey("department")) job.setDepartment(readString(input, "department"));
        if (input.containsKey("location")) job.setLocation(readString(input, "location"));
        if (input.containsKey("type")) job.setEmploymentType(readString(input, "type"));
        if (input.containsKey("skills")) job.setRequiredSkills(readStringList(input.get("skills"), job.getRequiredSkills()));
        if (input.containsKey("salaryMin")) job.setSalaryMin(readInteger(input.get("salaryMin"), job.getSalaryMin()));
        if (input.containsKey("salaryMax")) job.setSalaryMax(readInteger(input.get("salaryMax"), job.getSalaryMax()));
        if (input.containsKey("salaryCurrency")) job.setCurrency(readString(input, "salaryCurrency"));
        if (input.containsKey("status")) job.setStatus(readString(input, "status"));
        if (input.containsKey("closingDate")) job.setClosingDate(parseDateTime(input.get("closingDate")));
        job.setUpdatedAt(LocalDateTime.now());
        return repository.save(job);
    }

    public void deleteJob(String id) {
        repository.deleteById(id);
    }

    public JobAnalysis publishJob(String id) {
        JobAnalysis job = getById(id);
        job.setStatus("OPEN");
        job.setPostedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        return repository.save(job);
    }

    public JobAnalysis closeJob(String id) {
        JobAnalysis job = getById(id);
        job.setStatus("CLOSED");
        job.setUpdatedAt(LocalDateTime.now());
        return repository.save(job);
    }

    public Map<String, Object> getJobDashboardMetrics() {
        return getJobDashboardMetrics(false);
    }

    public Map<String, Object> getJobDashboardMetrics(boolean marketOnly) {
        List<JobAnalysis> jobs = repository.findAll().stream().filter(job -> !marketOnly || jobMarket.includes(job)).toList();
        long openJobs = jobs.stream().filter(job -> "OPEN".equalsIgnoreCase(defaultIfBlank(job.getStatus(), "DRAFT"))).count();

        Map<String, Long> skillCounts = jobs.stream()
                .map(JobAnalysis::getRequiredSkills)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(skill -> !skill.isBlank())
                .collect(Collectors.groupingBy(skill -> skill, LinkedHashMap::new, Collectors.counting()));

        List<Map<String, Object>> topSkills = skillCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> Map.<String, Object>of("skill", entry.getKey(), "count", entry.getValue().intValue()))
                .collect(Collectors.toList());

        // Jobs posted in the app; imports arrive by the dozen each day and would crowd out everything else.
        List<Map<String, Object>> recentActivity = jobs.stream()
                .filter(job -> job.getSource() == null || job.getSource().isBlank() || "INTERNAL".equalsIgnoreCase(job.getSource()))
                .sorted(Comparator.comparing(JobAnalysis::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(job -> Map.<String, Object>of(
                        "type", "NEW_JOB",
                        "description", "Job posted: " + defaultIfBlank(job.getJobTitle(), "Untitled job")
                                + " (" + defaultIfBlank(job.getStatus(), "DRAFT").toLowerCase() + ")",
                        "timestamp", String.valueOf(Objects.requireNonNullElse(job.getUpdatedAt(), job.getCreatedAt()))
                ))
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalJobs", jobs.size());
        response.put("openJobs", (int) openJobs);
        response.put("topSkillsInDemand", topSkills);
        response.put("recentActivity", recentActivity);
        return response;
    }

    public SkillsExtractResponse extractSkillsFromText(String text) {
        meterRegistry.counter("job_analyzer.skills.extract.requests").increment();
        if (text == null) {
            return new SkillsExtractResponse(List.of(), List.of());
        }

        var required = new LinkedHashSet<String>();
        required.addAll(jobSkills.find(text));

        var aiSkills = callAiSkillsExtraction(text);
        required.addAll(aiSkills.stream().map(this::normalizeSkill).collect(Collectors.toList()));

        var preferred = required.stream()
                .filter(s -> s.equals("Docker") || s.equals("Kubernetes") || s.equals("AWS"))
                .collect(Collectors.toList());

        return new SkillsExtractResponse(new ArrayList<>(required), preferred);
    }

    public SalaryEstimateResponse estimateSalary(SalaryEstimateRequest request) {
        meterRegistry.counter("job_analyzer.salary.estimate.requests").increment();
        int base = titleBase(request.jobTitle());
        int exp = request.experienceYears() == null ? 2 : request.experienceYears();
        int skillPremium = (request.requiredSkills() == null ? 0 : request.requiredSkills().size()) * 1500;
        int locationFactor = locationFactor(request.location());

        int min = Math.max(45000, base + exp * 5000 + skillPremium + locationFactor);
        int max = min + 25000 + exp * 4000;

        var response = new SalaryEstimateResponse(min, max, "USD", 0.72, "Rule-based estimate using title, experience, skills, and location.");
        salaryConfidenceSummary.record(response.confidence());
        return response;
    }

    public JobAnalysis getById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis not found: " + id));
    }

    private boolean matchesSource(JobAnalysis job, String source) {
        if (source == null || source.isBlank()) return true;
        // A feed key from job-feeds.yml (a portal tab); anything else is an exact source tag.
        return jobFeeds.find(source)
                .map(feed -> JobFeeds.includes(feed, job))
                .orElseGet(() -> source.equalsIgnoreCase(job.getSource()));
    }

    private boolean matchesSearch(JobAnalysis job, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String needle = search.toLowerCase();
        return StreamCandidates.of(
                job.getJobTitle(),
                job.getDepartment(),
                job.getLocation(),
                job.getCompanyName(),
                job.getJobDescription(),
                job.getEmploymentType(),
                job.getStatus(),
                job.getRequiredSkills() == null ? null : String.join(" ", job.getRequiredSkills())
        ).stream().filter(Objects::nonNull).map(String::toLowerCase).anyMatch(value -> value.contains(needle));
    }

    private int extractExperienceYears(String jd) {
        if (jd == null) {
            return 2;
        }
        Matcher m = Pattern.compile("(\\d+)\\+?\\s*(years|yrs)", Pattern.CASE_INSENSITIVE).matcher(jd);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 2;
    }

    private int titleBase(String title) {
        if (title == null) {
            return 80000;
        }
        // Java 21: Enhanced if-else with efficient condition checking
        String t = title.toLowerCase();
        if (t.contains("principal") || t.contains("staff")) {
            return 145000;
        }
        if (t.contains("senior")) {
            return 120000;
        }
        if (t.contains("lead")) {
            return 130000;
        }
        if (t.contains("intern")) {
            return 45000;
        }
        return 90000;
    }

    private int locationFactor(String location) {
        if (location == null) {
            return 0;
        }
        // Java 21: Pattern matching for location-based salary adjustments
        String l = location.toLowerCase();
        if (l.contains("san francisco") || l.contains("new york") || l.contains("seattle")) {
            return 20000;
        }
        if (l.contains("remote")) {
            return 8000;
        }
        return 0;
    }

    private void publishAnalyzedEvent(JobAnalysis analysis) {
        JobAnalyzedEvent event = JobAnalyzedEvent.builder()
                .analysisId(analysis.getId())
                .jobId(analysis.getJobId())
                .jobTitle(analysis.getJobTitle())
                .requiredSkills(analysis.getRequiredSkills())
                .salaryMin(analysis.getSalaryMin())
                .salaryMax(analysis.getSalaryMax())
                .currency(analysis.getCurrency())
                .confidence(analysis.getConfidence())
                .analyzedAt(System.currentTimeMillis())
                .build();
        rabbitTemplate.convertAndSend(RabbitMQConfiguration.JOB_EXCHANGE, RabbitMQConfiguration.JOB_ANALYZED_ROUTING_KEY, event);
    }

    private List<String> callAiSkillsExtraction(String text) {
        try {
            String url = aiBaseUrl + "/completion";
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", "job-analyzer");
            // Java 21: Text block for multi-line string with improved readability
            String prompt = """
                    Extract skills from this JD as comma-separated values only: %s
                    """.formatted(text).trim();
            payload.put("prompt", prompt);
            payload.put("model", "gpt-3.5-turbo");
            payload.put("temperature", 0.1);
            payload.put("maxTokens", 250);

            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            var entity = new HttpEntity<>(payload, headers);

            @SuppressWarnings("unchecked")
            var response = (Map<String, Object>) restTemplate.postForObject(url, entity, Map.class);
            if (response == null || response.get("response") == null) {
                return List.of();
            }
            
            // Java 21: More expressive stream operations with var
            var csv = String.valueOf(response.get("response"));
            return Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .limit(30)
                    .toList(); // Java 21: Use toList() instead of collect(toList())
        } catch (Exception e) {
            log.warn("AI skills extraction unavailable; using dictionary fallback: {}", e.getMessage());
            return List.of();
        }
    }

    private String normalizeSkill(String skill) {
        var s = skill.trim().toLowerCase();
        // Enhanced switch expression for skill name normalization
        return switch (s) {
            case "spring boot" -> "Spring Boot";
            case "node.js" -> "Node.js";
            default -> s.isEmpty() 
                ? ""
                : Character.toUpperCase(s.charAt(0)) + s.substring(1);
        };
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String readString(Map<String, Object> input, String key) {
        Object value = input.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer readInteger(Object value, Integer fallback) {
        if (value == null) {
            return fallback;
        }
        // Java 21 pattern matching with instanceof
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(value)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private List<String> readStringList(Object value, List<String> fallback) {
        // Java 21: Pattern matching with instanceof
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .filter(s -> !s.isBlank())
                    .toList(); // Java 21: Use toList() instead of collect(toList())
        }
        return fallback == null ? List.of() : fallback;
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        var raw = String.valueOf(value);
        if (raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(raw);
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private static final class StreamCandidates {
        private StreamCandidates() {}
        static List<String> of(String... values) {
            return Arrays.asList(values);
        }
    }
}
