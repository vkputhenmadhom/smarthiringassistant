package org.vinod.sha.analyzer.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vinod.sha.analyzer.service.JobSyncService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoints for the external job-board sync feature.
 *
 *  POST /api/jobs/sync/trigger  – run a sync immediately in a virtual thread (non-blocking)
 *  GET  /api/jobs/sync/status   – show last sync result + source config status + sync in progress
 *
 * Java 21: Uses virtual threads (Project Loom) for efficient background processing.
 */
@Slf4j
@RestController
@RequestMapping("sync")
public class JobSyncController {

    private final JobSyncService jobSyncService;

    public JobSyncController(JobSyncService jobSyncService) {
        this.jobSyncService = jobSyncService;
    }

    /**
     * Manually trigger a sync in a virtual thread (non-blocking).
     * Returns immediately with a status message and sync ID.
     * Monitor progress via GET /sync/status
     */
    @PostMapping("trigger")
    public ResponseEntity<Map<String, Object>> trigger() {
        log.info("[JobSyncController] Manual sync triggered via virtual thread");

        String syncId = jobSyncService.triggerSyncAsync();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Sync started in background");
        response.put("syncId", syncId);
        response.put("status", "IN_PROGRESS");
        response.put("checkStatusAt", "/api/jobs/sync/status");

        return ResponseEntity.accepted().body(response);
    }

    /**
     * Returns the current sync status:
     *  - whether sync is enabled
     *  - which sources are configured (API keys present)
     *  - result of the last sync run
     *  - whether a sync is currently in progress
     */
    @GetMapping("status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(jobSyncService.getStatus());
    }
}

