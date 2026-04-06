package org.vinod.sha.analyzer.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vinod.sha.analyzer.service.JobSyncService;

import java.util.Map;

/**
 * Endpoints for the external job-board sync feature.
 *
 *  POST /api/jobs/sync/trigger  – run a sync immediately (useful for demos/CI)
 *  GET  /api/jobs/sync/status   – show last sync result + source config status
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
     * Manually trigger a sync with all configured external sources.
     * Returns a summary of how many jobs were imported from each source.
     */
    @PostMapping("trigger")
    public ResponseEntity<Map<String, Object>> trigger() {
        log.info("[JobSyncController] Manual sync triggered");
        Map<String, Object> result = jobSyncService.triggerSync();
        return ResponseEntity.ok(result);
    }

    /**
     * Returns the current sync status:
     *  - whether sync is enabled
     *  - which sources are configured (API keys present)
     *  - result of the last sync run
     */
    @GetMapping("status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(jobSyncService.getStatus());
    }
}

