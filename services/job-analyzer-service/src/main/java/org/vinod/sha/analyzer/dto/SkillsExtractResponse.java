package org.vinod.sha.analyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillsExtractResponse {
    private List<String> requiredSkills;
    private List<String> preferredSkills;
}

