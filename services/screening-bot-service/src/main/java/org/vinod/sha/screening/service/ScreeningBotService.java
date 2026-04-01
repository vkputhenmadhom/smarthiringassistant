package org.vinod.sha.screening.service;

import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vinod.shared.config.RabbitMQConfiguration;
import org.vinod.shared.events.ScreeningCompensationRequestedEvent;
import org.vinod.shared.events.ScreeningCompletedEvent;
import org.vinod.sha.screening.dto.CreateSessionRequest;
import org.vinod.sha.screening.dto.DecisionResponse;
import org.vinod.sha.screening.dto.SubmitStageResponseRequest;
import org.vinod.sha.screening.entity.ScreeningSession;
import org.vinod.sha.screening.entity.StageResult;
import org.vinod.sha.screening.entity.WorkflowSagaState;
import org.vinod.sha.screening.repository.ScreeningSessionRepository;
import org.vinod.sha.screening.repository.WorkflowSagaStateRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ScreeningBotService {

    private final ScreeningSessionRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final MeterRegistry meterRegistry;
    private final WorkflowSagaStateRepository sagaStateRepository;

    public ScreeningBotService(ScreeningSessionRepository repository,
                               RabbitTemplate rabbitTemplate,
                               MeterRegistry meterRegistry,
                               WorkflowSagaStateRepository sagaStateRepository) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.meterRegistry = meterRegistry;
        this.sagaStateRepository = sagaStateRepository;
    }

    private DistributionSummary finalScoreSummary;

    @Value("${screening.bot.stages:initial,technical,behavioral}")
    private List<String> stages;

    @jakarta.annotation.PostConstruct
    void initMetrics() {
        this.finalScoreSummary = DistributionSummary.builder("screening.final.score")
                .baseUnit("points")
                .description("Final screening score distribution")
                .register(meterRegistry);
    }

    public ScreeningSession createSession(CreateSessionRequest request) {
        meterRegistry.counter("screening.sessions.created").increment();
        ScreeningSession session = ScreeningSession.builder()
                .candidateId(request.getCandidateId())
                .jobId(request.getJobId())
                .currentStage(normalizeStage(stages.get(0)))
                .status("IN_PROGRESS")
                .decision("PENDING")
                .stageResults(new ArrayList<>())
                .failureReasons(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        ScreeningSession saved = repository.save(session);

        sagaStateRepository.save(WorkflowSagaState.builder()
                .sagaId("screening-" + saved.getId())
                .workflowType("CANDIDATE_SCREENING")
                .aggregateId(saved.getId())
                .state("STARTED")
                .stepsCompleted(new ArrayList<>(List.of("session_created")))
                .failureReasons(new ArrayList<>())
                .startedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        return saved;
    }

    public ScreeningSession submitResponse(String sessionId, SubmitStageResponseRequest request) {
        meterRegistry.counter("screening.stage.responses.submitted").increment();
        ScreeningSession session = getSession(sessionId);
        if ("COMPLETED".equalsIgnoreCase(session.getStatus())) {
            return session;
        }

        String stage = normalizeStage(request.getStage() == null ? session.getCurrentStage() : request.getStage());
        StageResult result = evaluateStage(stage, request.getResponse());
        meterRegistry.counter(result.isPassed() ? "screening.stage.pass" : "screening.stage.fail", "stage", stage).increment();

        List<StageResult> results = session.getStageResults() == null ? new ArrayList<>() : session.getStageResults();
        results.add(result);
        session.setStageResults(results);

        if (!result.isPassed()) {
            List<String> reasons = session.getFailureReasons() == null ? new ArrayList<>() : session.getFailureReasons();
            reasons.addAll(result.getReasons());
            session.setFailureReasons(reasons);
        }

        session.setUpdatedAt(LocalDateTime.now());
        return repository.save(session);
    }

    public ScreeningSession advance(String sessionId) {
        ScreeningSession session = getSession(sessionId);
        if ("COMPLETED".equalsIgnoreCase(session.getStatus())) {
            return session;
        }

        int currentIndex = indexOfStage(session.getCurrentStage());
        boolean currentPassed = lastStagePassed(session);

        if (!currentPassed) {
            completeSession(session, "FAIL");
            return repository.save(session);
        }

        if (currentIndex >= stages.size() - 1) {
            completeSession(session, "PASS");
            return repository.save(session);
        }

        session.setCurrentStage(normalizeStage(stages.get(currentIndex + 1)));
        session.setUpdatedAt(LocalDateTime.now());
        return repository.save(session);
    }

    public ScreeningSession getSession(String sessionId) {
        return repository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Screening session not found: " + sessionId));
    }

    public DecisionResponse getDecision(String sessionId) {
        ScreeningSession s = getSession(sessionId);
        return DecisionResponse.builder()
                .sessionId(s.getId())
                .decision(s.getDecision())
                .finalScore(s.getFinalScore())
                .failureReasons(s.getFailureReasons())
                .build();
    }

    public List<ScreeningSession> listSessions() {
        return sortByUpdatedDesc(repository.findAll());
    }

    public List<ScreeningSession> listSessionsForCandidate(Long candidateId) {
        return sortByUpdatedDesc(repository.findByCandidateId(candidateId));
    }

    private List<ScreeningSession> sortByUpdatedDesc(List<ScreeningSession> sessions) {
        return sessions.stream()
                .sorted(Comparator.comparing(ScreeningSession::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getDashboardMetrics() {
        List<ScreeningSession> sessions = repository.findAll();
        long pending = sessions.stream().filter(s -> "IN_PROGRESS".equalsIgnoreCase(s.getStatus())).count();
        long completed = sessions.stream().filter(s -> "COMPLETED".equalsIgnoreCase(s.getStatus())).count();
        long hires = sessions.stream().filter(s -> "PASS".equalsIgnoreCase(s.getDecision())).count();
        long totalCandidates = sessions.stream().map(ScreeningSession::getCandidateId).filter(Objects::nonNull).distinct().count();
        long activeCandidates = sessions.stream()
                .filter(s -> "IN_PROGRESS".equalsIgnoreCase(s.getStatus()))
                .map(ScreeningSession::getCandidateId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        double averageMatchScore = sessions.stream()
                .map(ScreeningSession::getFinalScore)
                .filter(Objects::nonNull)
                .mapToDouble(score -> Math.max(0, Math.min(1, score / 100.0)))
                .average()
                .orElse(0.0);

        double hireRate = completed == 0 ? 0.0 : (double) hires / (double) completed;

        List<Map<String, Object>> stagePassRates = stages.stream()
                .map(this::normalizeStage)
                .map(stage -> {
                    List<StageResult> results = sessions.stream()
                            .flatMap(session -> (session.getStageResults() == null ? List.<StageResult>of() : session.getStageResults()).stream())
                            .filter(result -> stage.equalsIgnoreCase(normalizeStage(result.getStage())))
                            .collect(Collectors.toList());
                    int total = results.size();
                    long pass = results.stream().filter(StageResult::isPassed).count();
                    double passRate = total == 0 ? 0.0 : (double) pass / (double) total;
                    return Map.<String, Object>of("stage", stage, "passRate", passRate, "totalCount", total);
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> recentActivity = sessions.stream()
                .sorted(Comparator.comparing(ScreeningSession::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(session -> Map.<String, Object>of(
                        "type", "SCREENING_" + ("COMPLETED".equalsIgnoreCase(session.getStatus()) ? "COMPLETED" : "UPDATED"),
                        "description", "Session " + session.getId() + " is " + session.getStatus(),
                        "timestamp", String.valueOf(Objects.requireNonNullElse(session.getUpdatedAt(), session.getCreatedAt()))
                ))
                .collect(Collectors.toList());

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalCandidates", (int) totalCandidates);
        metrics.put("activeCandidates", (int) activeCandidates);
        metrics.put("pendingScreenings", (int) pending);
        metrics.put("completedScreenings", (int) completed);
        metrics.put("averageMatchScore", averageMatchScore);
        metrics.put("hireRate", hireRate);
        metrics.put("stagePassRates", stagePassRates);
        metrics.put("recentActivity", recentActivity);
        return metrics;
    }

    private StageResult evaluateStage(String stage, String response) {
        List<String> reasons = new ArrayList<>();
        int score = heuristicScore(response);

        if (response == null || response.isBlank()) {
            reasons.add(stage + " stage failed: no response provided");
        }
        if (response != null && response.length() < 30) {
            reasons.add(stage + " stage failed: response too short");
            score = Math.min(score, 35);
        }
        if (response != null && !response.toLowerCase().contains("example") && stage.equals("behavioral")) {
            reasons.add("behavioral stage requires concrete examples");
            score = Math.min(score, 55);
        }

        boolean passed = score >= 60 && reasons.stream().noneMatch(r -> r.contains("failed"));

        return StageResult.builder()
                .stage(stage)
                .passed(passed)
                .score(score)
                .response(response)
                .reasons(reasons)
                .evaluatedAt(LocalDateTime.now())
                .build();
    }

    private int heuristicScore(String response) {
        if (response == null || response.isBlank()) {
            return 5;
        }
        int score = Math.min(70, response.length() / 6);
        String r = response.toLowerCase();
        if (r.contains("impact") || r.contains("result")) {
            score += 15;
        }
        if (r.contains("metric") || r.contains("latency") || r.contains("availability")) {
            score += 10;
        }
        if (r.contains("example")) {
            score += 10;
        }
        return Math.min(score, 100);
    }

    private boolean lastStagePassed(ScreeningSession session) {
        if (session.getStageResults() == null || session.getStageResults().isEmpty()) {
            return false;
        }
        StageResult last = session.getStageResults().get(session.getStageResults().size() - 1);
        return last.isPassed();
    }

    private int indexOfStage(String stage) {
        String normalized = normalizeStage(stage);
        for (int i = 0; i < stages.size(); i++) {
            if (normalizeStage(stages.get(i)).equals(normalized)) {
                return i;
            }
        }
        return 0;
    }

    private void completeSession(ScreeningSession session, String decision) {
        session.setDecision(decision);
        session.setStatus("COMPLETED");
        session.setCompletedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        session.setFinalScore(calculateFinalScore(session));
        finalScoreSummary.record(session.getFinalScore());
        meterRegistry.counter("screening.sessions.completed", "decision", decision).increment();

        ScreeningCompletedEvent event = ScreeningCompletedEvent.builder()
                .sessionId(session.getId())
                .candidateId(session.getCandidateId())
                .jobId(session.getJobId())
                .decision(session.getDecision())
                .finalScore(session.getFinalScore())
                .failureReasons(session.getFailureReasons())
                .completedAt(System.currentTimeMillis())
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConfiguration.SCREENING_EXCHANGE,
                RabbitMQConfiguration.SCREENING_COMPLETED_ROUTING_KEY,
                event
        );

        WorkflowSagaState saga = sagaStateRepository.findByAggregateId(session.getId())
                .orElse(WorkflowSagaState.builder()
                        .sagaId("screening-" + session.getId())
                        .workflowType("CANDIDATE_SCREENING")
                        .aggregateId(session.getId())
                        .stepsCompleted(new ArrayList<>())
                        .failureReasons(new ArrayList<>())
                        .startedAt(LocalDateTime.now())
                        .build());

        List<String> steps = saga.getStepsCompleted() == null ? new ArrayList<>() : saga.getStepsCompleted();
        steps.add("screening_completed");
        saga.setStepsCompleted(steps);
        saga.setState("PASS".equalsIgnoreCase(decision) ? "COMPLETED" : "FAILED");
        saga.setUpdatedAt(LocalDateTime.now());
        saga.setEndedAt(LocalDateTime.now());
        saga.setFailureReasons(session.getFailureReasons());
        sagaStateRepository.save(saga);

        if ("FAIL".equalsIgnoreCase(decision)) {
            ScreeningCompensationRequestedEvent compensationEvent = ScreeningCompensationRequestedEvent.builder()
                    .sessionId(session.getId())
                    .candidateId(session.getCandidateId())
                    .jobId(session.getJobId())
                    .reason("Screening failed - trigger downstream compensation")
                    .failureReasons(session.getFailureReasons())
                    .requestedAt(System.currentTimeMillis())
                    .build();

            rabbitTemplate.convertAndSend(
                    RabbitMQConfiguration.SCREENING_EXCHANGE,
                    RabbitMQConfiguration.SCREENING_COMPENSATION_ROUTING_KEY,
                    compensationEvent
            );
        }
    }

    private double calculateFinalScore(ScreeningSession session) {
        if (session.getStageResults() == null || session.getStageResults().isEmpty()) {
            return 0.0;
        }
        return session.getStageResults().stream().mapToDouble(StageResult::getScore).average().orElse(0.0);
    }

    private String normalizeStage(String stage) {
        return stage == null ? "initial" : stage.trim().toLowerCase();
    }
}

