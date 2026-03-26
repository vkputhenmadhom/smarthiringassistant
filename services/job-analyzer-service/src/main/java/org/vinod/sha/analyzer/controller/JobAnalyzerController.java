package org.vinod.sha.analyzer.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vinod.sha.analyzer.dto.JobAnalyzeRequest;
import org.vinod.sha.analyzer.dto.SalaryEstimateRequest;
import org.vinod.sha.analyzer.dto.SalaryEstimateResponse;
import org.vinod.sha.analyzer.dto.SkillsExtractRequest;
import org.vinod.sha.analyzer.dto.SkillsExtractResponse;
import org.vinod.sha.analyzer.entity.JobAnalysis;
import org.vinod.sha.analyzer.service.JobAnalyzerService;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class JobAnalyzerController {

    private final JobAnalyzerService service;

    @PostMapping("analyze")
    public ResponseEntity<JobAnalysis> analyze(@RequestBody JobAnalyzeRequest request) {
        return ResponseEntity.ok(service.analyze(request));
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

