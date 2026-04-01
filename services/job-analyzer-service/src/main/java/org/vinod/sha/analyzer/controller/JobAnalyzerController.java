package org.vinod.sha.analyzer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vinod.sha.analyzer.dto.JobAnalyzeRequest;
import org.vinod.sha.analyzer.dto.SalaryEstimateRequest;
import org.vinod.sha.analyzer.dto.SalaryEstimateResponse;
import org.vinod.sha.analyzer.dto.SkillsExtractRequest;
import org.vinod.sha.analyzer.dto.SkillsExtractResponse;
import org.vinod.sha.analyzer.entity.JobAnalysis;
import org.vinod.sha.analyzer.service.JobAnalyzerService;

import java.util.Map;

@RestController
@RequestMapping("/")
public class JobAnalyzerController {

    private final JobAnalyzerService service;

    public JobAnalyzerController(JobAnalyzerService service) {
        this.service = service;
    }

    @PostMapping("analyze")
    public ResponseEntity<JobAnalysis> analyze(@RequestBody JobAnalyzeRequest request) {
        return ResponseEntity.ok(service.analyze(request));
    }

    @GetMapping("jobs")
    public ResponseEntity<Map<String, Object>> listJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(service.listJobs(page, size, status, search));
    }

    @PostMapping("jobs")
    public ResponseEntity<JobAnalysis> createJob(@RequestBody Map<String, Object> input) {
        return ResponseEntity.ok(service.createJobFromInput(input));
    }

    @GetMapping("jobs/{id}")
    public ResponseEntity<JobAnalysis> getJob(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("jobs/{id}")
    public ResponseEntity<JobAnalysis> updateJob(@PathVariable String id, @RequestBody Map<String, Object> input) {
        return ResponseEntity.ok(service.updateJob(id, input));
    }

    @DeleteMapping("jobs/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable String id) {
        service.deleteJob(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("jobs/{id}/publish")
    public ResponseEntity<JobAnalysis> publishJob(@PathVariable String id) {
        return ResponseEntity.ok(service.publishJob(id));
    }

    @PostMapping("jobs/{id}/close")
    public ResponseEntity<JobAnalysis> closeJob(@PathVariable String id) {
        return ResponseEntity.ok(service.closeJob(id));
    }

    @GetMapping("metrics/dashboard")
    public ResponseEntity<Map<String, Object>> dashboardMetrics() {
        return ResponseEntity.ok(service.getJobDashboardMetrics());
    }

    @PostMapping("extract-skills")
    public ResponseEntity<SkillsExtractResponse> extractSkills(@RequestBody SkillsExtractRequest request) {
        return ResponseEntity.ok(service.extractSkillsFromText(request.getText()));
    }

    @PostMapping("estimate-salary")
    public ResponseEntity<SalaryEstimateResponse> estimateSalary(@RequestBody SalaryEstimateRequest request) {
        return ResponseEntity.ok(service.estimateSalary(request));
    }

    @GetMapping("{analysisId}")
    public ResponseEntity<JobAnalysis> get(@PathVariable String analysisId) {
        return ResponseEntity.ok(service.getById(analysisId));
    }
}

