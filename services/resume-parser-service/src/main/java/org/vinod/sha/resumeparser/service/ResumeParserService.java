package org.vinod.sha.resumeparser.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vinod.sha.resumeparser.outbox.OutboxPublisher;
import org.vinod.sha.resumeparser.entity.*;
import org.vinod.sha.resumeparser.repository.ResumeRepository;
import org.vinod.shared.events.ResumeParseEvent;
import org.vinod.shared.text.SkillText;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
@Transactional
public class ResumeParserService {

    private final ResumeRepository resumeRepository;
    private final DocumentProcessor documentProcessor;
    private final OutboxPublisher outboxPublisher;
    private final AiResumeAnalyzer aiResumeAnalyzer;

    public ResumeParserService(ResumeRepository resumeRepository,
                               DocumentProcessor documentProcessor,
                               OutboxPublisher outboxPublisher,
                               AiResumeAnalyzer aiResumeAnalyzer) {
        this.resumeRepository = resumeRepository;
        this.documentProcessor = documentProcessor;
        this.outboxPublisher = outboxPublisher;
        this.aiResumeAnalyzer = aiResumeAnalyzer;
    }

    private static final String RESUME_EXCHANGE = "resume.exchange";
    private static final String RESUME_PARSED_ROUTING_KEY = "resume.parsed";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    public Resume parseResume(Long userId, String fileName, byte[] fileContent) throws Exception {
        long startTime = System.currentTimeMillis();

        // Validate file
        documentProcessor.validateFile(fileContent, fileName, MAX_FILE_SIZE);

        // Create resume entity
        String resumeId = UUID.randomUUID().toString();
        Resume resume = Resume.builder()
                .userId(userId)
                .resumeId(resumeId)
                .fileName(fileName)
                .fileContent(fileContent)
                .fileFormat(getFileFormat(fileName))
                .status(ParseStatus.PROCESSING)
                .build();

        resume = resumeRepository.save(resume);
        log.info("Created resume entity: {}", resumeId);

        try {
            // Extract text from document
            String extractedText = documentProcessor.extractText(fileContent, getFileFormat(fileName));

            // Parse resume content
            ParsedResumeData parsedData = parseResumeContent(extractedText);
            aiResumeAnalyzer.analyze(userId, extractedText).ifPresentOrElse(
                    ai -> {
                        applyAi(parsedData, ai);
                        log.info("Parsed resume {} with AI: {} skills, {} years, {} preferred locations", resumeId,
                                parsedData.getSkills().size(), parsedData.getTotalExperienceYears(), ai.preferredLocations() == null ? 0 : ai.preferredLocations().size());
                    },
                    () -> log.info("Parsed resume {} with the rule-based parser only", resumeId));

            // Save parsed data
            resume.setParsedData(parsedData);
            resume.setStatus(ParseStatus.SUCCESS);
            resume.setParsedAt(LocalDateTime.now());
            resume = resumeRepository.save(resume);

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Successfully parsed resume: {} in {}ms", resumeId, processingTime);

            // Publish event
            publishResumeParseEvent(resume, processingTime, "SUCCESS");

            return resume;
        } catch (Exception e) {
            log.error("Failed to parse resume: {}", resumeId, e);
            resume.setStatus(ParseStatus.FAILED);
            resume.setErrorMessage(e.getMessage());
            resume = resumeRepository.save(resume);

            long processingTime = System.currentTimeMillis() - startTime;
            publishResumeParseEvent(resume, processingTime, "FAILED");

            throw e;
        }
    }

    public Resume getResumeByResumeId(String resumeId) {
        return resumeRepository.findByResumeId(resumeId)
                .orElseThrow(() -> new RuntimeException("Resume not found: " + resumeId));
    }

    /**
     * Sends the user's latest parsed resume to matching again, so new or changed jobs are matched without a new
     * upload (or another AI call). False when the user has no parsed resume.
     */
    public boolean rematch(Long userId) {
        return resumeRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .filter(resume -> resume.getStatus() == ParseStatus.SUCCESS)
                .map(resume -> {
                    publishResumeParseEvent(resume, 0, "SUCCESS");
                    log.info("Re-sent resume {} of user {} to matching", resume.getResumeId(), userId);
                    return true;
                })
                .orElse(false);
    }

    public List<Resume> getUserResumes(Long userId) {
        return resumeRepository.findByUserId(userId);
    }

    private ParsedResumeData parseResumeContent(String text) {
        ParsedResumeData parsedData = ParsedResumeData.builder()
                .fullName(extractFullName(text))
                .email(extractEmail(text))
                .phone(extractPhone(text))
                .location(extractLocation(text))
                .summary(extractSummary(text))
                .skills(extractSkills(text))
                .certifications(extractCertifications(text))
                .totalExperienceYears(extractTotalExperience(text))
                .experiences(extractExperiences(text))
                .educations(extractEducations(text))
                .build();

        return parsedData;
    }

    /** 2–4 words of letters (initials, hyphens and apostrophes allowed): "Jane A. Doe", "RAVI KUMAR" */
    private static final Pattern NAME_LINE = Pattern.compile("\\p{L}[\\p{L}.'-]*(\\s+\\p{L}[\\p{L}.'-]*){1,3}");
    private static final Pattern NOT_A_NAME = Pattern.compile(
            "(?i)\\b(resume|curriculum|vitae|cv|profile|summary|objective|contact|engineer|developer|manager|architect)\\b");

    /**
     * The first of the opening lines that looks like a person's name. PDF text extraction does not always put
     * the name first, so a tagline such as "Relocated from US | Open to Remote" is skipped. Blank if none fits.
     */
    static String extractFullName(String text) {
        String[] lines = text.split("\n");
        for (int i = 0; i < Math.min(10, lines.length); i++) {
            String line = lines[i].trim().replaceAll("\\s+", " ");
            if (NAME_LINE.matcher(line).matches() && !NOT_A_NAME.matcher(line).find()) {
                return line;
            }
        }
        return "";
    }

    private String extractEmail(String text) {
        Pattern pattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return "";
    }

    private String extractPhone(String text) {
        Pattern pattern = Pattern.compile("\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return "";
    }

    private String extractLocation(String text) {
        // Simple location extraction - look for common state abbreviations or country names
        Pattern pattern = Pattern.compile("(New York|California|Texas|Florida|NY|CA|TX|FL|USA|US)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return "";
    }

    private String extractSummary(String text) {
        String[] sections = text.split("(?i)(SUMMARY|PROFESSIONAL SUMMARY|OBJECTIVE)");
        if (sections.length > 1) {
            String summary = sections[1].split("(?i)(EXPERIENCE|SKILLS|EDUCATION)")[0].trim();
            return summary.length() > 500 ? summary.substring(0, 500) : summary;
        }
        return "";
    }

    private List<String> extractSkills(String text) {
        Set<String> commonSkills = Set.of(
                "Java", "Python", "JavaScript", "Spring", "React", "Angular",
                "SQL", "MongoDB", "PostgreSQL", "Docker", "Kubernetes",
                "AWS", "Azure", "GCP", "Git", "Jenkins", "CI/CD",
                "REST API", "GraphQL", "gRPC", "Microservices",
                "HTML", "CSS", "Node.js", "C++", "Go", "Rust"
        );

        return new ArrayList<>(SkillText.find(text, commonSkills));
    }

    private List<String> extractCertifications(String text) {
        List<String> certifications = new ArrayList<>();
        Pattern pattern = Pattern.compile("(?i)(certified|certification|certificate)\\s+([^,\n]+)");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String cert = matcher.group(2).trim();
            if (!cert.isEmpty()) {
                certifications.add(cert);
            }
        }
        return certifications;
    }

    private Double extractTotalExperience(String text) {
        Pattern pattern = Pattern.compile("(\\d+)\\s+years?\\s+(?i)(of\\s+)?experience");
        Matcher matcher = pattern.matcher(text);

        List<Integer> years = new ArrayList<>();
        while (matcher.find()) {
            years.add(Integer.parseInt(matcher.group(1)));
        }

        return years.isEmpty() ? 0.0 : years.stream().mapToDouble(Double::valueOf).average().orElse(0.0);
    }

    private List<Experience> extractExperiences(String text) {
        List<Experience> experiences = new ArrayList<>();
        // This is a simplified version - in production, use NLP models
        // For now, return empty list
        return experiences;
    }

    private List<Education> extractEducations(String text) {
        List<Education> educations = new ArrayList<>();
        // This is a simplified version - in production, use NLP models
        // For now, return empty list
        return educations;
    }

    private String getFileFormat(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1) : "";
    }

    /**
     * The AI's reading wins where it found a value; email and phone fall back to the patterns, and skills are
     * the AI's plus any the dictionary found that it did not. Location comes from the AI only: the pattern
     * finds US states anywhere in the text ("Relocated from US").
     */
    static void applyAi(ParsedResumeData data, AiResumeAnalyzer.AiResume ai) {
        if (notBlank(ai.fullName())) data.setFullName(ai.fullName().trim());
        if (notBlank(ai.email())) data.setEmail(ai.email().trim());
        if (notBlank(ai.phone())) data.setPhone(ai.phone().trim());
        data.setLocation(notBlank(ai.currentLocation()) ? ai.currentLocation().trim() : "");
        if (ai.preferredLocations() != null && !ai.preferredLocations().isEmpty()) {
            data.setPreferredLocations(String.join("; ", ai.preferredLocations().stream()
                    .filter(ResumeParserService::notBlank).map(String::trim).distinct().toList()));
        }
        if (ai.totalExperienceYears() != null && ai.totalExperienceYears() > 0) {
            data.setTotalExperienceYears(ai.totalExperienceYears());
        }
        if (notBlank(ai.summary())) data.setSummary(ai.summary().trim());

        LinkedHashMap<String, String> skills = new LinkedHashMap<>();
        Stream.concat(
                        ai.skills() == null ? Stream.empty() : ai.skills().stream(),
                        data.getSkills() == null ? Stream.empty() : data.getSkills().stream())
                .filter(ResumeParserService::notBlank)
                .forEach(skill -> skills.putIfAbsent(skill.trim().toLowerCase(Locale.ROOT), skill.trim()));
        data.setSkills(new ArrayList<>(skills.values()));

        if (ai.experience() != null && !ai.experience().isEmpty()) {
            data.setExperiences(new ArrayList<>(ai.experience().stream()
                    .map(job -> Experience.builder()
                            .company(job.company()).jobTitle(job.title())
                            .startDate(job.startDate()).endDate(job.endDate())
                            .description(job.description())
                            .build())
                    .toList()));
        }
        if (ai.education() != null && !ai.education().isEmpty()) {
            data.setEducations(new ArrayList<>(ai.education().stream()
                    .map(school -> Education.builder()
                            .institution(school.institution()).degree(school.degree()).fieldOfStudy(school.field())
                            .graduationDate(school.graduationYear() == null ? null : String.valueOf(school.graduationYear()))
                            .build())
                    .toList()));
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private void publishResumeParseEvent(Resume resume, long processingTime, String status) {
        ParsedResumeData data = resume.getParsedData();
        
        List<String> skills = data != null ? data.getSkills() : new ArrayList<>();
        
        ResumeParseEvent event = ResumeParseEvent.builder()
                .resumeId(resume.getResumeId())
                .userId(resume.getUserId())
                .fileName(resume.getFileName())
                .skills(skills)
                .experienceYears(data != null ? data.getTotalExperienceYears() : 0.0)
                .location(data != null ? data.getLocation() : "")
                .preferredLocations(data == null || data.getPreferredLocations() == null || data.getPreferredLocations().isBlank()
                        ? List.of() : List.of(data.getPreferredLocations().split(";\\s*")))
                .parsedAt(System.currentTimeMillis())
                .status(status)
                .errorMessage(resume.getErrorMessage())
                .build();

        try {
            outboxPublisher.enqueue(RESUME_EXCHANGE, RESUME_PARSED_ROUTING_KEY, "ResumeParseEvent", event);
            log.info("Queued ResumeParseEvent to outbox for resume: {}", resume.getResumeId());
        } catch (Exception e) {
            log.error("Failed to enqueue ResumeParseEvent for resume: {}", resume.getResumeId(), e);
        }
    }
}

