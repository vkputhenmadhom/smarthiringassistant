package org.vinod.interview.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.vinod.interview.dto.AnswerFeedbackResponse;
import org.vinod.interview.dto.GenerateQuestionsRequest;
import org.vinod.interview.entity.InterviewQuestion;
import org.vinod.interview.repository.InterviewQuestionRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InterviewPrepService {

    private static final Logger log = LoggerFactory.getLogger(InterviewPrepService.class);

    private final InterviewQuestionRepository repository;
    private final RestTemplate restTemplate;

    public InterviewPrepService(InterviewQuestionRepository repository, RestTemplate restTemplate) {
        this.repository = repository;
        this.restTemplate = restTemplate;
    }

    @Value("${interview.question.generation.count:5}")
    private int defaultCount;

    @Value("${services.ai-integration.base-url:http://localhost:8008/api/ai}")
    private String aiBaseUrl;

    public List<InterviewQuestion> generateQuestions(GenerateQuestionsRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        int count = request.getCount() == null || request.getCount() <= 0 ? defaultCount : request.getCount();
        String prompt = buildQuestionPrompt(request, count);

        String aiText = callAiCompletion(prompt, request.getUserId());
        List<String> questions = parseQuestions(aiText, count);
        if (questions.isEmpty()) {
            questions = fallbackQuestions(request, count);
        }

        List<String> skills = Optional.ofNullable(request.getSkills()).orElseGet(List::of);
        LocalDateTime now = LocalDateTime.now();

        List<InterviewQuestion> entities = questions.stream()
                .map(q -> new InterviewQuestion(
                        null,
                        q,
                        defaultIfBlank(request.getCategory(), "TECHNICAL"),
                        defaultIfBlank(request.getDifficulty(), "MEDIUM"),
                        defaultIfBlank(request.getRole(), "General"),
                        null,
                        new ArrayList<>(),
                        new ArrayList<>(skills),
                        defaultIfBlank(request.getUserId(), "system"),
                        now,
                        now
                ))
                .collect(Collectors.toList());

        return repository.saveAll(entities);
    }

    public List<InterviewQuestion> listQuestions(String topic, String difficulty, String category) {
        if (notBlank(topic) && notBlank(difficulty)) {
            return repository.findByTopicIgnoreCaseAndDifficultyIgnoreCase(topic, difficulty);
        }
        if (notBlank(topic)) {
            return repository.findByTopicIgnoreCase(topic);
        }
        if (notBlank(difficulty)) {
            return repository.findByDifficultyIgnoreCase(difficulty);
        }
        if (notBlank(category)) {
            return repository.findByCategoryIgnoreCase(category);
        }
        return repository.findAll();
    }

    public InterviewQuestion getQuestion(String id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Question not found: " + id));
    }

    public AnswerFeedbackResponse evaluateAnswer(String questionId, String userId, String answer) {
        InterviewQuestion q = getQuestion(questionId);

        String prompt = "Evaluate this interview answer in JSON with fields score(0-100), summary, strengths(array), improvements(array). " +
                "Question: " + q.getQuestion() + " Answer: " + answer;
        String ai = callAiCompletion(prompt, userId);

        // Lightweight parse fallback if AI response is plain text.
        int score = heuristicScore(q.getQuestion(), answer);
        return new AnswerFeedbackResponse(
                questionId,
                score,
                ai == null || ai.isBlank() ? "Answer evaluated using heuristic." : ai,
                List.of("Relevant response", "Covers key points"),
                List.of("Add concrete examples", "Quantify impact")
        );
    }

    private String callAiCompletion(String prompt, String userId) {
        try {
            String url = aiBaseUrl + "/completion";
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", defaultIfBlank(userId, "system"));
            payload.put("prompt", prompt);
            payload.put("model", "gpt-3.5-turbo");
            payload.put("temperature", 0.7);
            payload.put("maxTokens", 800);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            if (response == null) {
                return "";
            }
            Object text = response.get("response");
            return text == null ? "" : String.valueOf(text);
        } catch (Exception e) {
            log.warn("AI completion unavailable, using fallback generation. {}", e.getMessage());
            return "";
        }
    }

    private String buildQuestionPrompt(GenerateQuestionsRequest request, int count) {
        return "Generate " + count + " interview questions for role " + defaultIfBlank(request.getRole(), "Software Engineer") +
                ", category " + defaultIfBlank(request.getCategory(), "technical") +
                ", difficulty " + defaultIfBlank(request.getDifficulty(), "medium") +
                ". Skills: " + String.join(", ", Optional.ofNullable(request.getSkills()).orElseGet(List::of)) +
                ". Return as numbered list only.";
    }

    private List<String> parseQuestions(String aiText, int count) {
        if (aiText == null || aiText.isBlank()) {
            return List.of();
        }
        return Arrays.stream(aiText.split("\\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.replaceFirst("^\\d+[).]\\s*", ""))
                .filter(s -> s.endsWith("?") || s.length() > 15)
                .distinct()
                .limit(count)
                .collect(Collectors.toList());
    }

    private List<String> fallbackQuestions(GenerateQuestionsRequest request, int count) {
        List<String> skills = Optional.ofNullable(request.getSkills()).orElseGet(() -> List.of("Java", "Spring"));
        List<String> base = new ArrayList<>();
        for (String skill : skills) {
            base.add("Explain a real project where you used " + skill + " and the impact you delivered?");
            base.add("What are common pitfalls when using " + skill + " and how do you avoid them?");
        }
        if (base.isEmpty()) {
            base = List.of(
                    "Tell me about a challenging bug you solved recently?",
                    "How do you design scalable services for high traffic?",
                    "How do you ensure code quality in your team?"
            );
        }
        return base.stream().limit(count).collect(Collectors.toList());
    }

    private int heuristicScore(String question, String answer) {
        if (answer == null || answer.isBlank()) {
            return 10;
        }
        String normalizedAnswer = answer.toLowerCase(Locale.ROOT);
        int score = Math.min(60, answer.length() / 10);
        if (normalizedAnswer.contains("example")) {
            score += 10;
        }
        if (normalizedAnswer.contains("result") || normalizedAnswer.contains("impact")) {
            score += 10;
        }
        if (question != null && !question.isBlank()) {
            long overlaps = Arrays.stream(question.toLowerCase(Locale.ROOT).split("\\W+"))
                    .filter(token -> token.length() > 3)
                    .distinct()
                    .filter(normalizedAnswer::contains)
                    .count();
            score += (int) Math.min(overlaps * 5, 20);
        }
        return Math.min(score, 100);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String defaultIfBlank(String value, String fallback) {
        return notBlank(value) ? value : fallback;
    }
}

