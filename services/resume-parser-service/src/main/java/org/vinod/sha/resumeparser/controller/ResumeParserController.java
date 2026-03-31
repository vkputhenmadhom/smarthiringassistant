package org.vinod.sha.resumeparser.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.vinod.sha.resumeparser.dto.ResumeParseResponse;
import org.vinod.sha.resumeparser.dto.ResumeStatusResponse;
import org.vinod.sha.resumeparser.entity.Resume;
import org.vinod.sha.resumeparser.service.ResumeParserService;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/")
public class ResumeParserController {

    private final ResumeParserService resumeParserService;

    public ResumeParserController(ResumeParserService resumeParserService) {
        this.resumeParserService = resumeParserService;
    }

    @PostMapping("parse")
    public ResponseEntity<ResumeParseResponse> parseResume(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ResumeParseResponse.builder().message("Resume file is required").build());
            }
            if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ResumeParseResponse.builder().message("File name is required").build());
            }

            log.info("Received resume parse request for file: {}", file.getOriginalFilename());

            // Extract user ID from authentication
            Long userId = extractUserIdFromAuth(authentication);

            // Parse resume
            Resume resume = resumeParserService.parseResume(
                    userId,
                    file.getOriginalFilename(),
                    file.getBytes()
            );

            ResumeParseResponse response = ResumeParseResponse.builder()
                    .resumeId(resume.getResumeId())
                    .fileName(resume.getFileName())
                    .status(resume.getStatus().toString())
                    .message("Resume parsed successfully")
                    .build();

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error reading file", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResumeParseResponse.builder()
                            .message("Error reading file: " + e.getMessage())
                            .build());
        } catch (Exception e) {
            log.error("Error parsing resume", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResumeParseResponse.builder()
                            .message("Error parsing resume: " + e.getMessage())
                            .build());
        }
    }

    @GetMapping("{resumeId}")
    public ResponseEntity<ResumeStatusResponse> getResumeStatus(
            @PathVariable String resumeId) {
        
        try {
            Resume resume = resumeParserService.getResumeByResumeId(resumeId);

            ResumeStatusResponse response = ResumeStatusResponse.builder()
                    .resumeId(resume.getResumeId())
                    .fileName(resume.getFileName())
                    .status(resume.getStatus().toString())
                    .createdAt(resume.getCreatedAt().toString())
                    .parsedAt(resume.getParsedAt() != null ? resume.getParsedAt().toString() : null)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching resume status", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ResumeStatusResponse.builder()
                            .message("Resume not found")
                            .build());
        }
    }

    @GetMapping("user/resumes")
    public ResponseEntity<List<ResumeStatusResponse>> getUserResumes(
            Authentication authentication) {
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Long userId = extractUserIdFromAuth(authentication);
            List<Resume> resumes = resumeParserService.getUserResumes(userId);

            List<ResumeStatusResponse> responses = resumes.stream()
                    .map(resume -> ResumeStatusResponse.builder()
                            .resumeId(resume.getResumeId())
                            .fileName(resume.getFileName())
                            .status(resume.getStatus().toString())
                            .createdAt(resume.getCreatedAt().toString())
                            .parsedAt(resume.getParsedAt() != null ? resume.getParsedAt().toString() : null)
                            .build())
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("Error fetching user resumes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Long extractUserIdFromAuth(Authentication authentication) {
        // In a real scenario, extract from JWT token or UserDetails
        // For now, return a dummy value
        return 1L;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception e) {
        log.error("Error in resume parser controller", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal server error: " + e.getMessage());
    }
}

