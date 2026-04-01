package org.vinod.sha.analyzer.service;

import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
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
public class JobAnalyzerService {

    private final JobAnalysisRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate restTemplate;
    private final MeterRegistry meterRegistry;

    public JobAnalyzerService(JobAnalysisRepository repository,
                              RabbitTemplate rabbitTemplate,
                              RestTemplate restTemplate,
                              MeterRegistry meterRegistry) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.restTemplate = restTemplate;
        this.meterRegistry = meterRegistry;
    }

    private DistributionSummary salaryConfidenceSummary;

    @Value("${services.ai-integration.base-url:http://localhost:8008/api/ai}")
    private String aiBaseUrl;

    private static final Set<String> SKILL_DICTIONARY = Set.of(
            "java", "spring", "spring boot", "react", "angular", "node.js", "python", "sql", "postgresql",
            "mongodb", "redis", "docker", "kubernetes", "aws", "azure", "gcp", "graphql", "grpc",
            "microservices", "kafka", "rabbitmq", "jenkins", "terraform", "typescript", "go"
    );

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

        SkillsExtractResponse skills = extractSkillsFromText(request.getJobDescription());
        SalaryEstimateResponse salary = estimateSalary(SalaryEstimateRequest.builder()
                .jobTitle(request.getJobTitle())
                .location(request.getLocation())
                .experienceYears(extractExperienceYears(request.getJobDescription()))
                .requiredSkills(skills.getRequiredSkills())
                .build());

        JobAnalysis analysis = JobAnalysis.builder()
                .jobId(defaultIfBlank(request.getJobId(), UUID.randomUUID().toString()))
                .jobTitle(request.getJobTitle())
                .companyName(request.getCompanyName())
                .department(null)
                .location(request.getLocation())
                .employmentType(defaultIfBlank(request.getEmploymentType(), "FULL_TIME"))
                .jobDescription(request.getJobDescription())
                .requiredSkills(skills.getRequiredSkills())
                .preferredSkills(skills.getPreferredSkills())
                .minExperienceYears(extractExperienceYears(request.getJobDescription()))
                .maxExperienceYears(Math.max(extractExperienceYears(request.getJobDescription()) + 2, 3))
                .salaryMin(salary.getMin())
                .salaryMax(salary.getMax())
                .currency(salary.getCurrency())
                .confidence(salary.getConfidence())
                .status("ANALYZED")
                .notes(salary.getRationale())
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
        List<JobAnalysis> all = repository.findAll().stream()
                .sorted(Comparator.comparing(JobAnalysis::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(job -> status == null || status.isBlank() || status.equalsIgnoreCase(defaultIfBlank(job.getStatus(), "DRAFT")))
                .filter(job -> matchesSearch(job, search))
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
        JobAnalyzeRequest request = JobAnalyzeRequest.builder()
                .jobId(readString(input, "jobId"))
                .jobTitle(readString(input, "title"))
                .companyName(readString(input, "companyName"))
                .location(readString(input, "location"))
                .employmentType(readString(input, "type"))
                .jobDescription(readString(input, "description"))
                .build();

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
        List<JobAnalysis> jobs = repository.findAll();
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

        List<Map<String, Object>> recentActivity = jobs.stream()
                .sorted(Comparator.comparing(JobAnalysis::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(job -> Map.<String, Object>of(
                        "type", "JOB_UPDATED",
                        "description", defaultIfBlank(job.getJobTitle(), "Untitled job") + " is " + defaultIfBlank(job.getStatus(), "DRAFT"),
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
            return SkillsExtractResponse.builder().requiredSkills(List.of()).preferredSkills(List.of()).build();
        }

        Set<String> required = new LinkedHashSet<>();
        for (String skill : SKILL_DICTIONARY) {
            if (text.toLowerCase().contains(skill)) {
                required.add(normalizeSkill(skill));
            }
        }

        List<String> aiSkills = callAiSkillsExtraction(text);
        required.addAll(aiSkills.stream().map(this::normalizeSkill).collect(Collectors.toList()));

        List<String> preferred = required.stream().filter(s -> s.equals("Docker") || s.equals("Kubernetes") || s.equals("AWS")).collect(Collectors.toList());

        return SkillsExtractResponse.builder()
                .requiredSkills(new ArrayList<>(required))
                .preferredSkills(preferred)
                .build();
    }

    public SalaryEstimateResponse estimateSalary(SalaryEstimateRequest request) {
        meterRegistry.counter("job_analyzer.salary.estimate.requests").increment();
        int base = titleBase(request.getJobTitle());
        int exp = request.getExperienceYears() == null ? 2 : request.getExperienceYears();
        int skillPremium = (request.getRequiredSkills() == null ? 0 : request.getRequiredSkills().size()) * 1500;
        int locationFactor = locationFactor(request.getLocation());

        int min = Math.max(45000, base + exp * 5000 + skillPremium + locationFactor);
        int max = min + 25000 + exp * 4000;

        SalaryEstimateResponse response = SalaryEstimateResponse.builder()
                .min(min)
                .max(max)
                .currency("USD")
                .confidence(0.72)
                .rationale("Rule-based estimate using title, experience, skills, and location.")
                .build();
        salaryConfidenceSummary.record(response.getConfidence());
        return response;
    }

    public JobAnalysis getById(String id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Analysis not found: " + id));
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
            payload.put("prompt", "Extract skills from this JD as comma-separated values only: " + text);
            payload.put("model", "gpt-3.5-turbo");
            payload.put("temperature", 0.1);
            payload.put("maxTokens", 250);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            if (response == null || response.get("response") == null) {
                return List.of();
            }
            String csv = String.valueOf(response.get("response"));
            return Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .limit(30)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("AI skills extraction unavailable; using dictionary fallback: {}", e.getMessage());
            return List.of();
        }
    }

    private String normalizeSkill(String skill) {
        String s = skill.trim().toLowerCase();
        if (s.equals("spring boot")) {
            return "Spring Boot";
        }
        if (s.equals("node.js")) {
            return "Node.js";
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
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
        if (value instanceof Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).map(String::valueOf).filter(s -> !s.isBlank()).collect(Collectors.toList());
        }
        return fallback == null ? List.of() : fallback;
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value);
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
