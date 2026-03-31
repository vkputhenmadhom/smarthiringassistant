package org.vinod.interview.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vinod.interview.dto.AnswerFeedbackRequest;
import org.vinod.interview.dto.AnswerFeedbackResponse;
import org.vinod.interview.dto.GenerateQuestionsRequest;
import org.vinod.interview.entity.InterviewQuestion;
import org.vinod.interview.service.InterviewPrepService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/")
public class InterviewPrepController {

    private final InterviewPrepService service;

    public InterviewPrepController(InterviewPrepService service) {
        this.service = service;
    }

    @PostMapping("questions/generate")
    public ResponseEntity<List<InterviewQuestion>> generate(@RequestBody GenerateQuestionsRequest request) {
        return ResponseEntity.ok(service.generateQuestions(request));
    }

    @GetMapping("questions")
    public ResponseEntity<List<InterviewQuestion>> list(
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(service.listQuestions(topic, difficulty, category));
    }

    @GetMapping("questions/{id}")
    public ResponseEntity<InterviewQuestion> get(@PathVariable String id) {
        return ResponseEntity.ok(service.getQuestion(id));
    }

    @PostMapping("questions/{id}/feedback")
    public ResponseEntity<AnswerFeedbackResponse> feedback(
            @PathVariable String id,
            @RequestBody AnswerFeedbackRequest request) {
        return ResponseEntity.ok(service.evaluateAnswer(id, request.userId(), request.answer()));
    }
}

