package org.vinod.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewPrepService {

    private final InterviewQuestionRepository repository;
    private final RestTemplate restTemplate;

    @Value("${interview.question.generation.count:5}")
    private int defaultCount;

    @Value("${services.ai-integration.base-url:http://localhost:8008/api/ai}")
    private String aiBaseUrl;

    public List<InterviewQuestion> generateQuestions(GenerateQuestionsRequest request) {
        int count = request.getCount() == null || request.getCount() <= 0 ? defaultCount : request.getCount();
        String prompt = buildQuestionPrompt(request, count);

        String aiText = callAiCompletion(prompt, request.getUserId());
        List<String> questions = parseQuestions(aiText, count);
        if (questions.isEmpty()) {
            questions = fallbackQuestions(request, count);
        }

        List<InterviewQuestion> entities = questions.stream()
                .map(q -> InterviewQuestion.builder()
                        .question(q)
                        .category(defaultIfBlank(request.getCategory(), "TECHNICAL"))
                        .difficulty(defaultIfBlank(request.getDifficulty(), "MEDIUM"))
                        .topic(defaultIfBlank(request.getRole(), "General"))
                        .keyPoints(new ArrayList<>())
                        .tags(request.getSkills())
                        .createdBy(defaultIfBlank(request.getUserId(), "system"))
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build())
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
        return AnswerFeedbackResponse.builder()
                .questionId(questionId)
                .score(score)
                .summary(ai == null || ai.isBlank() ? "Answer evaluated using heuristic." : ai)
                .strengths(List.of("Relevant response", "Covers key points"))
                .improvements(List.of("Add concrete examples", "Quantify impact"))
                .build();
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
                ". Skills: " + String.join(", ", Optional.ofNullable(request.getSkills()).orElse(List.of())) +
                ". Return as numbered list only.";
    }

    private List<String> parseQuestions(String aiText, int count) {
        if (aiText == null || aiText.isBlank()) {
            return List.of();
        }
        List<String> result = Arrays.stream(aiText.split("\\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.replaceFirst("^\\d+[).]\\s*", ""))
                .filter(s -> s.endsWith("?") || s.length() > 15)
                .distinct()
                .limit(count)
                .collect(Collectors.toList());
        return result;
    }

    private List<String> fallbackQuestions(GenerateQuestionsRequest request, int count) {
        List<String> skills = Optional.ofNullable(request.getSkills()).orElse(List.of("Java", "Spring"));
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
        int score = Math.min(70, answer.length() / 8);
        if (answer.toLowerCase().contains("example")) {
            score += 10;
        }
        if (answer.toLowerCase().contains("result") || answer.toLowerCase().contains("impact")) {
            score += 10;
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

