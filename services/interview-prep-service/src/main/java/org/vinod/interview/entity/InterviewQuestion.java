package org.vinod.interview.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "interview_questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewQuestion {

    @Id
    private String id;

    private String question;
    private String category; // TECHNICAL, BEHAVIORAL, SYSTEM_DESIGN
    private String difficulty; // EASY, MEDIUM, HARD
    private String topic; // Java, Spring, React, etc.
    private String expectedAnswer;
    private List<String> keyPoints;
    private List<String> tags;

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}

