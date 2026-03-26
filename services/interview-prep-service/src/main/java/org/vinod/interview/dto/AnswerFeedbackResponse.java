package org.vinod.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnswerFeedbackResponse {
    private String questionId;
    private int score;
    private String summary;
    private List<String> strengths;
    private List<String> improvements;
}

