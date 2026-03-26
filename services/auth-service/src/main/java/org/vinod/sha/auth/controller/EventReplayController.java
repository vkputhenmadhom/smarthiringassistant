package org.vinod.sha.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vinod.sha.auth.outbox.OutboxRelay;

import java.util.Map;

@RestController
@RequestMapping("/internal/events")
@RequiredArgsConstructor
public class EventReplayController {

    private final OutboxRelay outboxRelay;

    @PostMapping("/replay")
    public ResponseEntity<Map<String, Object>> replayFailed() {
        int count = outboxRelay.replayFailedEvents();
        return ResponseEntity.ok(Map.of("requeued", count));
    }
}

