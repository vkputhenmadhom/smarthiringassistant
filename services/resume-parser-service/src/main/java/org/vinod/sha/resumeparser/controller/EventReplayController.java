package org.vinod.sha.resumeparser.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vinod.sha.resumeparser.outbox.OutboxRelay;

import java.util.Map;

@RestController
@RequestMapping("/internal/events")
public class EventReplayController {

    private final OutboxRelay outboxRelay;

    public EventReplayController(OutboxRelay outboxRelay) {
        this.outboxRelay = outboxRelay;
    }

    @PostMapping("/replay")
    public ResponseEntity<Map<String, Object>> replayFailed() {
        int count = outboxRelay.replayFailedEvents();
        return ResponseEntity.ok(Map.of("requeued", count));
    }
}

