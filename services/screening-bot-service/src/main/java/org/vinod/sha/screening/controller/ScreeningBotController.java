package org.vinod.sha.screening.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vinod.sha.screening.dto.CreateSessionRequest;
import org.vinod.sha.screening.dto.DecisionResponse;
import org.vinod.sha.screening.dto.SubmitStageResponseRequest;
import org.vinod.sha.screening.entity.ScreeningSession;
import org.vinod.sha.screening.service.ScreeningBotService;

@RestController
@RequestMapping("/")
public class ScreeningBotController {

    private final ScreeningBotService service;

    public ScreeningBotController(ScreeningBotService service) {
        this.service = service;
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
}

