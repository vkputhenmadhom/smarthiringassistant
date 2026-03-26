package org.vinod.sha.resumeparser.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vinod.sha.resumeparser.outbox.OutboxPublisher;
import org.vinod.sha.resumeparser.entity.*;
import org.vinod.sha.resumeparser.repository.ResumeRepository;
import org.vinod.shared.events.ResumeParseEvent;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ResumeParserService {

    private final ResumeRepository resumeRepository;
    private final DocumentProcessor documentProcessor;
    private final OutboxPublisher outboxPublisher;

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

    private String extractFullName(String text) {
        // Try to extract name from first few lines
        String[] lines = text.split("\n");
        for (int i = 0; i < Math.min(5, lines.length); i++) {
            String line = lines[i].trim();
            if (!line.isEmpty() && !isEmail(line) && !isPhone(line)) {
                return line;
            }
        }
        return "Unknown";
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

        List<String> foundSkills = new ArrayList<>();
        for (String skill : commonSkills) {
            if (text.toLowerCase().contains(skill.toLowerCase())) {
                foundSkills.add(skill);
            }
        }
        return foundSkills;
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

    private boolean isEmail(String text) {
        return text.contains("@");
    }

    private boolean isPhone(String text) {
        return text.matches(".*\\d{3}.*\\d{3}.*\\d{4}.*");
    }

    private String getFileFormat(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1) : "";
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

