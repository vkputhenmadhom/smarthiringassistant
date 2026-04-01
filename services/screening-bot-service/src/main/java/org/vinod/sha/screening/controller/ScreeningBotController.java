package org.vinod.sha.screening.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vinod.sha.screening.dto.CreateSessionRequest;
import org.vinod.sha.screening.dto.DecisionResponse;
import org.vinod.sha.screening.dto.SubmitStageResponseRequest;
import org.vinod.sha.screening.entity.ScreeningSession;
import org.vinod.sha.screening.security.JwtPrincipal;
import org.vinod.sha.screening.security.JwtPrincipalExtractor;
import org.vinod.sha.screening.service.ScreeningBotService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/")
public class ScreeningBotController {

    private final ScreeningBotService service;
    private final JwtPrincipalExtractor jwtPrincipalExtractor;

    public ScreeningBotController(ScreeningBotService service,
                                  JwtPrincipalExtractor jwtPrincipalExtractor) {
        this.service = service;
        this.jwtPrincipalExtractor = jwtPrincipalExtractor;
    }

    @PostMapping("sessions")
    public ResponseEntity<ScreeningSession> createSession(@RequestBody CreateSessionRequest request) {
        return ResponseEntity.ok(service.createSession(request));
    }

    @PostMapping("sessions/{sessionId}/responses")
    public ResponseEntity<ScreeningSession> submitResponse(
            @PathVariable String sessionId,
            @RequestBody SubmitStageResponseRequest request) {
        return ResponseEntity.ok(service.submitResponse(sessionId, request));
    }

    @PostMapping("sessions/{sessionId}/advance")
    public ResponseEntity<ScreeningSession> advance(@PathVariable String sessionId) {
        return ResponseEntity.ok(service.advance(sessionId));
    }

    @GetMapping("sessions/{sessionId}")
    public ResponseEntity<ScreeningSession> getSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(service.getSession(sessionId));
    }

    @GetMapping("sessions/{sessionId}/decision")
    public ResponseEntity<DecisionResponse> getDecision(@PathVariable String sessionId) {
        return ResponseEntity.ok(service.getDecision(sessionId));
    }

    @GetMapping("sessions")
    public ResponseEntity<List<ScreeningSession>> listSessions() {
        return ResponseEntity.ok(service.listSessions());
    }

    @GetMapping("sessions/my")
    public ResponseEntity<List<ScreeningSession>> mySessions(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        JwtPrincipal principal = jwtPrincipalExtractor.extractFromAuthorizationHeader(authorizationHeader).orElse(null);
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        String role = principal.role() == null ? "" : principal.role().toUpperCase();
        if (!"JOB_SEEKER".equals(role) && !"CANDIDATE".equals(role)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(service.listSessionsForCandidate(principal.userId()));
    }

    @GetMapping("metrics/dashboard")
    public ResponseEntity<Map<String, Object>> dashboardMetrics() {
        return ResponseEntity.ok(service.getDashboardMetrics());
    }
}

