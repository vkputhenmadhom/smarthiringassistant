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
public class GenerateQuestionsRequest {
    private String userId;
    private String role;
    private String category;
    private String difficulty;
    private List<String> skills;
    private Integer count;
}

