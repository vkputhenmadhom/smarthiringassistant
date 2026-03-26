package org.vinod.sha.matcher.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vinod.sha.matcher.dto.MatchRequest;
import org.vinod.sha.matcher.dto.MatchResponse;
import org.vinod.sha.matcher.dto.MatchUpdateRequest;
import org.vinod.sha.matcher.entity.CandidateMatch;
import org.vinod.sha.matcher.entity.MatchStatus;
import org.vinod.sha.matcher.service.CandidateMatcherService;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class CandidateMatcherController {

    private final CandidateMatcherService candidateMatcherService;

    @PostMapping("match")
    public ResponseEntity<List<MatchResponse>> matchCandidate(@RequestBody MatchRequest request) {
        log.info("Received match request for candidate: {}", request.getCandidateId());

        try {
            var matches = candidateMatcherService.matchCandidateWithJobs(
                    request.getCandidateId(),
                    request.getSkills(),
                    request.getExperienceYears(),
                    request.getLocation()
            );

            List<MatchResponse> responses = matches.stream()
                    .map(this::convertToResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("Error matching candidate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("match-job")
    public ResponseEntity<MatchResponse> matchCandidateWithJob(@RequestBody MatchRequest request) {
        log.info("Received match request for candidate: {} and job: {}", 
                request.getCandidateId(), request.getJobId());

        try {
            CandidateMatch match = candidateMatcherService.matchCandidateWithJob(
                    request.getCandidateId(),
                    request.getJobId(),
                    request.getSkills(),
                    request.getExperienceYears(),
                    request.getLocation()
            );

            return ResponseEntity.ok(convertToResponse(match));

        } catch (Exception e) {
            log.error("Error matching candidate with job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("job/{jobId}/matches")
    public ResponseEntity<List<MatchResponse>> getMatchesForJob(@PathVariable String jobId) {
        log.info("Fetching matches for job: {}", jobId);

        try {
            var matches = candidateMatcherService.getMatchesForJob(jobId);
            List<MatchResponse> responses = matches.stream()
                    .map(this::convertToResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("Error fetching matches for job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("candidate/{candidateId}/matches")
    public ResponseEntity<List<MatchResponse>> getMatchesForCandidate(@PathVariable Long candidateId) {
        log.info("Fetching matches for candidate: {}", candidateId);

        try {
            var matches = candidateMatcherService.getMatchesForCandidate(candidateId);
            List<MatchResponse> responses = matches.stream()
                    .map(this::convertToResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("Error fetching matches for candidate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("{matchId}")
    public ResponseEntity<MatchResponse> getMatch(@PathVariable String matchId) {
        log.info("Fetching match: {}", matchId);

        try {
            CandidateMatch match = candidateMatcherService.getMatch(matchId);
            return ResponseEntity.ok(convertToResponse(match));

        } catch (Exception e) {
            log.error("Error fetching match", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("{matchId}")
    public ResponseEntity<MatchResponse> updateMatchStatus(
            @PathVariable String matchId,
            @RequestBody MatchUpdateRequest request) {
        log.info("Updating match: {} status to: {}", matchId, request.getStatus());

        try {
            CandidateMatch match = candidateMatcherService.updateMatchStatus(
                    matchId,
                    MatchStatus.valueOf(request.getStatus()),
                    request.getComments()
            );

            return ResponseEntity.ok(convertToResponse(match));

        } catch (Exception e) {
            log.error("Error updating match status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private MatchResponse convertToResponse(CandidateMatch match) {
        return MatchResponse.builder()
                .matchId(match.getMatchId())
                .candidateId(match.getCandidateId())
                .jobId(match.getJobId())
                .overallScore(match.getOverallScore())
                .skillMatchPercentage(match.getSkillMatchPercentage())
                .experienceMatchPercentage(match.getExperienceMatchPercentage())
                .locationMatchPercentage(match.getLocationMatchPercentage())
                .status(match.getStatus().toString())
                .createdAt(match.getCreatedAt().toString())
                .build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        log.error("Error in matcher controller", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal server error: " + e.getMessage());
    }
}

